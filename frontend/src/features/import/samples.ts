// Synthetic sample messages for the import page, copied from samples/referral-sample.json and
// samples/referral-sample.xml. The external references (GP-UI-SAMPLE-*) and MRNs (MRN-9004xx)
// differ from the repository samples so the UI never collides with back-end test data.
// Importing a sample twice shows the "already imported" result, because import is idempotent
// on externalRef.

export const SAMPLE_JSON = `{
  "externalRef": "GP-UI-SAMPLE-001",
  "referringGp": "Dr. A. Byrne, Riverside Medical Centre",
  "clinicCode": "GASTRO",
  "patient": { "mrn": "MRN-900481", "firstName": "Sam", "lastName": "Test", "dateOfBirth": "1968-04-12" },
  "clinicalNotes": "58-year-old with 6 weeks of change in bowel habit and 5kg unintentional weight loss. Iron deficiency anaemia on bloods."
}
`

export const SAMPLE_XML = `<?xml version="1.0" encoding="UTF-8"?>
<referral externalRef="GP-UI-SAMPLE-002">
  <referringGp>Dr. C. Walsh, Harbour Family Practice</referringGp>
  <clinicCode>DERMATOLOGY</clinicCode>
  <patient mrn="MRN-900482">
    <firstName>Alex</firstName>
    <lastName>Sample</lastName>
    <dateOfBirth>1990-09-30</dateOfBirth>
  </patient>
  <clinicalNotes>Long-standing mild eczema on both hands, managed with emollients. Requesting review of treatment options.</clinicalNotes>
</referral>
`
