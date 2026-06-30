/* Static mock data for policyholder portal */
/* Contains policies and recent claims for demo purposes */

export const policyholder = {
  name: "Jake",
  memberId: "ZIG-2024-00742",
};

export const policies = [
  {
    id: "AUTO-9281",
    type: "Auto",
    icon: "🚗",
    status: "Active",
    vehicle: "2022 Honda Civic",
    vin: "1HGFE2F59NH****",
    coverage: "$100,000 / $300,000",
    deductible: "$500",
    premium: "$142/mo",
    effectiveDate: "2024-01-15",
    expirationDate: "2025-01-15",
  },
  {
    id: "PROP-4410",
    type: "Property",
    icon: "🏠",
    status: "Active",
    address: "742 Evergreen Terrace",
    coverage: "$450,000",
    deductible: "$1,000",
    premium: "$198/mo",
    effectiveDate: "2024-03-01",
    expirationDate: "2025-03-01",
  },
  {
    id: "COMM-7733",
    type: "Commercial",
    icon: "🏢",
    status: "Active",
    businessName: "Jake's Pixel Repair Shop",
    coverageType: "General Liability",
    coverage: "$1,000,000",
    deductible: "$2,500",
    premium: "$385/mo",
    effectiveDate: "2024-06-01",
    expirationDate: "2025-06-01",
  },
];

export const recentClaims = [
  {
    id: "CLM-20241201",
    policyId: "AUTO-9281",
    type: "Auto",
    date: "2024-12-01",
    description: "Fender bender - rear collision",
    status: "Approved",
    amount: "$3,200",
  },
  {
    id: "CLM-20241115",
    policyId: "PROP-4410",
    type: "Property",
    date: "2024-11-15",
    description: "Storm damage - roof repair",
    status: "In Review",
    amount: "$8,750",
  },
  {
    id: "CLM-20240930",
    policyId: "AUTO-9281",
    type: "Auto",
    date: "2024-09-30",
    description: "Windshield replacement",
    status: "Paid",
    amount: "$450",
  },
  {
    id: "CLM-20240815",
    policyId: "COMM-7733",
    type: "Commercial",
    date: "2024-08-15",
    description: "Customer slip and fall",
    status: "Denied",
    amount: "$12,000",
  },
];
