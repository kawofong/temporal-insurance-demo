// Mutable workflow state for a CAT event entity.
// The batch-iterator cursor lives in CATEventInput; this state holds the durable,
// query-visible facts (lifecycle status, expected/opened counts, declaration time).
package com.ziggy.insurance.domains.cat;

public class CATEventState {

    private String catEventId;
    private String eventName;
    private String affectedRegion;
    private CATEventLifecycle status;
    private int totalClaimsExpected;
    private int totalClaimsOpened;
    private long declaredAt;

    public CATEventState() {}

    public static CATEventState fromInput(CATEventInput input) {
        CATEventState s = new CATEventState();
        s.catEventId = input.catEventId();
        s.eventName = input.eventName();
        s.affectedRegion = input.affectedRegion();
        s.status = CATEventLifecycle.DECLARED;
        s.totalClaimsExpected = input.totalClaimsToGenerate();
        s.totalClaimsOpened = input.totalClaimsOpened();
        s.declaredAt = input.declaredAt();
        return s;
    }

    public String getCatEventId() { return catEventId; }
    public void setCatEventId(String catEventId) { this.catEventId = catEventId; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getAffectedRegion() { return affectedRegion; }
    public void setAffectedRegion(String affectedRegion) { this.affectedRegion = affectedRegion; }

    public CATEventLifecycle getStatus() { return status; }
    public void setStatus(CATEventLifecycle status) { this.status = status; }

    public int getTotalClaimsExpected() { return totalClaimsExpected; }
    public void setTotalClaimsExpected(int totalClaimsExpected) { this.totalClaimsExpected = totalClaimsExpected; }

    public int getTotalClaimsOpened() { return totalClaimsOpened; }
    public void setTotalClaimsOpened(int totalClaimsOpened) { this.totalClaimsOpened = totalClaimsOpened; }

    public long getDeclaredAt() { return declaredAt; }
    public void setDeclaredAt(long declaredAt) { this.declaredAt = declaredAt; }
}
