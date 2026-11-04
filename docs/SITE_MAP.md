# Meuhedet site map

Endpoints and field names used by the personal area. No values, samples, list sizes or formats are recorded here.

- Host: `https://online.meuhedet.co.il`. Login: `https://login.meuhedet.co.il`.
- **Auth:** HttpOnly session cookies. There is no `Authorization` header and no token in web storage.
- **Common:** `?uniq_param=<timestamp>` (some use `uniq`) as a cache buster. Site headers: `X-requested-with: XMLHttpRequest`, `locale: he-IL`, `x-platform: 1`, `x-node-id: <CMS page id>` (differs per page).
- **Active person** is held on the server. All data endpoints return data for the currently selected family member.
- Treat every field as optional. Unknown fields are ignored.

## Identity / family
| Endpoint | Method | Response fields |
|---|---|---|
| `/umbraco/api/WeakPersonalInfoApi/GetCurrentMemberinfo?uniq=` | GET | `Id, IdType, FullName, FirstName, LastName, BirthDate, Gender, Age, IsChild, Selected, Guid, IsHeadOrSpouse, RelationType` |
| `/api/Personal/User?currentNodeId=&uniq=` | GET | `CurrentUser`, `LoginUser`, `FamilyMembers[]`, `GeneticFamilyMembers[]` (same member fields), `IsFamilyMember`, `LastLoginDateTime` |
| family switch | cookie | Set cookie `meuhedet.current.member2` (domain `.meuhedet.co.il`, path `/`) to the member's `Guid` (from `GeneticFamilyMembers[]`), then reload. Clear it to go back to the logged-in person. No request is sent. |

## Lab results
| Endpoint | Method | Request | Response fields |
|---|---|---|---|
| `/umbraco/api/LabApi/Stickers` | POST | `{FromDate, ToDate}` | `Items[]{StickerId, LabCode, TestDate, DoctorFirstName, DoctorLastName, DoctorLicense, InstituteName, PdfGuid, FilesCount}, TotalCount` |
| `/api/labs/{LabCode}/stickers/{StickerId}?date=yyyyMMdd` | GET | – | `detailsSection{summary{labCode, labName, stickerId, testDateTime, incomplete, notes}, doctors[]{name, license}, files[]}`, `testSections[]{code, tests[]{code, records[]{recordType, panicLevel, title, note{header, text}, scale{minRange, maxRange, value, charSign, measurementUnit}, single{value, charSign, measurementUnit, remark}, multiple, tableRow}}}` |
| `/umbraco/api/LabApi/HistoryTestsSearchBarData` | GET | – | `Tests[]{Key, Value}` |
| `/umbraco/api/LabApi/HistoryTests` | POST | `{Tests:[code]}` | `ShowTestsGraph, TestDesc, Items[]{StickerId, LabCode, TestDate, Result, MeasureUnit, NormFrom, NormTo, TestNormLow, TestNormHigh, TestValue, GraphicScale, PdfGuid, DoctorFirstName, DoctorLastName, InstituteName}` |
| `/api/medications/info/{code}` | GET | – | may return 409 `{type, status, detail}`. Treat as "no info". |

## Medications
| Endpoint | Method | Response fields |
|---|---|---|
| `/api/medications/active/all` | GET | `[]{id, description, flags{permenant, newPrescription, nearestPrescriptionStart, nearestPrescriptionEnd, digitalSignature, …}, doctors[]}` |
| `/api/medications/meds/purchased` | GET | `Items[]{Date, CatalogNumber, Description, DoctorName, Pharmacy, Quantity{Full, Single}}` |
| `/api/medications/meds/approvals` | GET | `Approvals[]{Description, Status, ParticipationPercent, ExpirationDate, DoctorName, File, Remarks}` |
| `/api/medications/prescriptions` | GET | `prescriptions[]{doctorName, doctorSpecialty, prescriptionNumber, prescriptionSource, createdAt, startsAt, endsAt, fileKey, medicines[]{catalogNumber, description}, digitalSignature}` |

## Login and session
- `/` → 302 to `login.meuhedet.co.il/connect/authorize` (OpenID Connect) → `/Account/Login`.
- Step 1: form POST `/Account/FirstStepLogin` with `IdType, Username, MobilePhoneNumber, __RequestVerificationToken`.
- Step 2: form POST `/Account/SecondStepLogin` with `Otp, Key, Username, IdType, MobilePhone`.
- → `/connect/authorize/callback` → auto-posted form to `https://online.meuhedet.co.il/signin-oidc` (`id_token, scope, state, session_state`) → 302 `/`.
- After about 10 minutes idle, the page goes to `/umbraco/Surface/AuthSurface/Signout`. While the login site's session is still valid, it signs back in silently through the same OIDC redirects. Once that has expired too, data requests end on the login page (HTML).

## Files (PDF)
| What | Address |
|---|---|
| Lab result | `GET /api/labs/{LabCode}/stickers/{StickerId}/pdf/{yyyyMMdd}` |
| Medication approval | `GET /api/medications/meds/approval?key={File}` |
| ECG / institute | `GET /umbraco/surface/ExaminationsSurface/GetInstituteFile?testDate={dd/MM/yyyy HH:mm:ss}&instId={InstitueId}&instType={InstituteType}&requestType=&fileId=` |
| Document / referral | `GET /umbraco/api/OnlineCommunicationApi/ReferenceFile?guid=&documentKey=&formCode=&formSourceSystem=` |
| Prescription | TBD (not captured) |

## Documents and record
| Endpoint | Method | Request | Response fields |
|---|---|---|---|
| `/umbraco/api/OnlineCommunicationApi/GetVisitsRefs` | GET | – | `Items[]{FormId, FormDate, FormTime, FormDoctorName, FormDoctorSpec, DocumentType, DocumentTypeDesc, DocumentDescription, DocumentKey, DocumentGuid, FormCode, FormSourceSystem, Available}` |
| `/umbraco/api/OnlineCommunicationApi/ReferenceFile?guid=&documentKey=&formCode=&formSourceSystem=` | GET | – | PDF |
| `/api/visits/products/summaries` | GET | – | `summaries[]` (item fields TBD) |
| `/umbraco/api/OnlineCommunicationApi/GetVisitsApprovals` | GET | – | `Items[]` (item fields TBD) |
| `/api/ExaminationsApi/Search` | POST | `{FromDate, ToDate}` | `Items[]{TestDate, InstituteName, Description, IsEKGResult, IsInstituteResult, Documents[], GuidId, FileExist, FilesCount}` |
| `/umbraco/api/NursesApi/VaccinesData` | GET | – | `Items[]{Date, Code, Name, ByInfantCare}` |
| `/umbraco/api/PersonalInfoApi/medicalPassport` | GET | – | `PurchasedMedications[], PermanentMedications[], Appointments[], InstitutionsResults[], CustomerLabTests[]{TestDate, TestName, TestData}` |
| `/api/commitment/search?fromDate=` | GET | – | `[]` (item fields TBD) |
| `/umbraco/api/AppointmentApi/VisitsAndAppointments` | POST | TBD | appointments |

## Still to capture
- Prescription file download.
- Diagnoses / sensitivities (no such page found).
