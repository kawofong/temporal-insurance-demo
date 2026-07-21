// Cross-language serialization contract (spec §6.3 / §9).
// The Java claim workflow invokes the Python agents as untyped child workflows, so the two must
// agree on the exact JSON wire format. These tests serialize each Java mirror record with the
// SAME data converter the workflow uses (GlobalDataConverter) and assert the JSON matches a
// fixture captured from the corresponding Pydantic model — and that the agent reports Java
// consumes deserialize back into the mirror records. A matching Python test
// (agents/tests/test_serialization_contract.py) asserts the Pydantic side against the same
// fixtures, so drift on either side breaks a build.
//
// Null-valued fields are normalised away before comparison: an omitted key and an explicit null
// are equivalent on the wire here (Pydantic defaults the missing optional to None, and Jackson
// reads it back as null), so the contract is about key names and non-null values.
package com.ziggy.insurance.domains.claim.property.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.EncodingKeys;
import io.temporal.common.converter.GlobalDataConverter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSerializationContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AgentPropertyClaim CLAIM = new AgentPropertyClaim(
        "clm-a1b2c3d4", "pol-100200", "ph-42", "cat-hurricane-2026", "MAJOR_DAMAGE",
        "Hurricane-force winds tore off roughly half the roof shingles.",
        1_760_000_000_000L, "742 Evergreen Terrace, Springfield", "SINGLE_FAMILY");

    private static final AgentCoverage COVERAGE = new AgentCoverage(true, "HO3", 1000, null);

    private static final AgentDamageAssessment ASSESSMENT = new AgentDamageAssessment(
        "Roof and window damage with water intrusion to the ceiling and drywall.", 24_500);

    private static final AgentApprovalRecommendation APPROVAL = new AgentApprovalRecommendation(
        "adj-ai-agent", 23_500, "Estimated repair cost minus the policy deductible.");

    @Test
    void fieldAdjusterRequestMatchesFixture() {
        assertSerialisationMatches(
            new AgentFieldAdjusterRequest(CLAIM, COVERAGE), "field_adjuster_request.json");
    }

    @Test
    void fieldAdjusterReportMatchesFixture() {
        assertSerialisationMatches(
            new FieldAdjusterReport(ASSESSMENT, APPROVAL), "field_adjuster_report.json");
    }

    @Test
    void claimDecisionRequestMatchesFixture() {
        assertSerialisationMatches(
            new AgentClaimDecisionRequest(CLAIM, COVERAGE, ASSESSMENT), "claim_decision_request.json");
    }

    @Test
    void claimDecisionReportMatchesFixture() {
        assertSerialisationMatches(
            new ClaimDecisionReport(true, 23_500, "adj-ai-agent",
                "Covered peril; repair cost exceeds the deductible.", null,
                "Coverage is verified and the assessed repair cost exceeds the deductible, "
                    + "so the payout is approved."),
            "claim_decision_report.json");
    }

    // The agent output Java consumes must deserialise from the Python wire JSON back into the
    // mirror records via the same converter, honouring @JsonProperty and @JsonIgnoreProperties.
    @Test
    void fieldAdjusterReportDeserialisesFromFixture() {
        FieldAdjusterReport report = fromPayload(
            loadFixture("field_adjuster_report.json"), FieldAdjusterReport.class);
        assertThat(report.assessment().summary())
            .isEqualTo("Roof and window damage with water intrusion to the ceiling and drywall.");
        assertThat(report.assessment().estimatedCost()).isEqualTo(24_500);
        assertThat(report.approval().adjusterId()).isEqualTo("adj-ai-agent");
        assertThat(report.approval().approvedPayoutAmount()).isEqualTo(23_500);
    }

    @Test
    void claimDecisionReportDeserialisesFromFixture() {
        ClaimDecisionReport report = fromPayload(
            loadFixture("claim_decision_report.json"), ClaimDecisionReport.class);
        assertThat(report.approved()).isTrue();
        assertThat(report.approvedPayoutAmount()).isEqualTo(23_500);
        assertThat(report.adjusterId()).isEqualTo("adj-ai-agent");
        assertThat(report.rejectionReason()).isNull();
        assertThat(report.toApprovalRequest().adjusterId()).isEqualTo("adj-ai-agent");
        assertThat(report.toApprovalRequest().approvedPayoutAmount()).isEqualTo(23_500);
    }

    // Extra unknown keys from the Python side (forward-compat) must not break deserialisation.
    @Test
    void unknownFieldsAreIgnoredOnDeserialisation() {
        String withExtra = """
            {"approved": false, "approved_payout_amount": 0, "adjuster_id": "adj-ai-agent",
             "notes": "", "rejection_reason": "not covered", "rationale": "denied",
             "confidence": 0.42}
            """;
        ClaimDecisionReport report = fromPayload(withExtra, ClaimDecisionReport.class);
        assertThat(report.approved()).isFalse();
        assertThat(report.toDenialRequest().reason()).isEqualTo("not covered");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private void assertSerialisationMatches(Object javaValue, String fixture) {
        String wire = GlobalDataConverter.get().toPayload(javaValue).orElseThrow()
            .getData().toStringUtf8();
        JsonNode actual = normalise(readTree(wire));
        JsonNode expected = normalise(readTree(loadFixture(fixture)));
        assertThat(actual).isEqualTo(expected);
    }

    private static <T> T fromPayload(String json, Class<T> type) {
        Payload payload = Payload.newBuilder()
            .putMetadata(EncodingKeys.METADATA_ENCODING_KEY,
                ByteString.copyFromUtf8("json/plain"))
            .setData(ByteString.copyFrom(json, StandardCharsets.UTF_8))
            .build();
        return GlobalDataConverter.get().fromPayload(payload, type, type);
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String loadFixture(String name) {
        try (var in = AgentSerializationContractTest.class.getResourceAsStream("/agents/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Recursively drops null-valued object fields so an explicit null and an omitted key compare
    // equal (see the class comment for why that is the correct wire semantics here).
    private static JsonNode normalise(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isNull()) {
                    out.set(field.getKey(), normalise(field.getValue()));
                }
            }
            return out;
        }
        return node;
    }
}
