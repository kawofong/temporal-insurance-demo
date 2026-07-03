/* Static mock data for policyholder portal */
/* Contains policies and recent claims for demo purposes */

export const policyholder = {
  name: "Jake",
  memberId: "jake-from-state-farm",
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
