# AI SKILL MANAGEMENT

## User Authentication

### Software Requirements / DEV Handoff Specification

**Document Version:** 1.8 
**Status:** Updated  
**Module:** Authentication  
**Audience:** PO / BA / UX/UI / Developer / QA / DB Developer

---

# 1. Document Purpose

This document defines the functional requirements, business rules, UI behavior, API specifications, OTP/email flow, database model, validation rules and acceptance criteria for the Authentication module.

The Authentication module includes:

- User Registration (with mandatory Email OTP verification)
- Login (by Email)
- Login Attempt Management
- Forgot Password (by Email)
- OTP Generation
- OTP Email
- OTP Verification
- Password Reset
- Password Security
- Authentication Audit

---

# 2. Scope

## 2.1 In Scope

| ID | Function |
|---|---|
| AUTH-01 | User Registration (Email OTP verified) |
| AUTH-02 | User Login (by Email) |
| AUTH-03 | Login Attempt Management |
| AUTH-04 | Forgot Password (by Email) |
| AUTH-05 | OTP Generation |
| AUTH-06 | OTP Email Sending |
| AUTH-07 | OTP Verification |
| AUTH-08 | Password Reset |
| AUTH-09 | Password Security |
| AUTH-10 | Authentication Audit |

## 2.2 Out of Scope

The following items are not included in this document:

- SSO
- Logout management
- Password expiration after 3 months
- Mandatory password change

---

# 3. User Roles Related to Authentication

| Role | Authentication Behavior |
|---|---|
| NORMAL_USER | Can register and use Login / Forgot Password |
| ADMIN_USER | Uses the same authentication flow |

Role is **not selectable during self-registration**.

All self-registered users are created with:

```text
ROLE = NORMAL_USER
```

---

# 4. User Status

Authentication recognizes two user statuses:

| Status | Login | Forgot Password | OTP | Reset Password |
|---|---:|---:|---:|---:|
| ACTIVE | Yes | Yes | Yes | Yes |
| DEACTIVE | No | Yes | Yes | Yes |

### Important Business Rule

A DEACTIVE user is allowed to recover the account through Forgot Password.

However:

> **OTP verification alone does not activate the user.**

The user becomes ACTIVE only after Password Reset succeeds.

```text
DEACTIVE
    ↓
Forgot Password
    ↓
OTP Verification
    ↓
DEACTIVE
    ↓
Reset Password Success
    ↓
ACTIVE
```

---

# 5. Authentication Flow

```text
                    ┌─────────────┐
                    │    LOGIN    │  (Email + Password)
                    └──────┬──────┘
                           │
             ┌─────────────┴─────────────┐
             │                           │
         Register                  Forgot Password
             │                           │
             ▼                           ▼
      Enter Name/Email/           Enter Email
      Password + Send OTP               │
             │                           ▼
             ▼                       Generate OTP
      Send Verification OTP              │
             │                           ▼
             ▼                       Send OTP Email
      Verify OTP                         │
             │                           ▼
             ▼                       Verify OTP
      Create Account                     │
             │                           ▼
             ▼                       Reset Password
           LOGIN                       ┌──────┴──────┐
                                        │             │
                                     ACTIVE        DEACTIVE
                                        │             │
                                        │             ▼
                                        │           ACTIVE
                                        │
                                        ▼
                                       LOGIN
```

---

# 6. Login

## 6.1 Login Credentials

| Field | Required | Description |
|---|---:|---|
| Email | Yes | Login ID |
| Password | Yes | User password |

Login ID is **Email** (case-insensitive).

The following are **not** Login IDs:

- User ID (DB-generated BIGINT — not known to the user)
- User Name

> **Note:** Because `USER_ID` is a DB-generated BIGINT that the user does not know, Email is the only user-entered identifier and is therefore the Login ID.

---

# 7. Login UI Wireframe

```text
┌──────────────────────────────────────────────┐
│                                              │
│             AI SKILL MANAGEMENT              │
│                                              │
│  Email *                                     │
│  ┌────────────────────────────────────────┐  │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Password *                                  │
│  ┌────────────────────────────────────── 👁 ┐ │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│                 Forgot Password?             │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │                 LOG IN                 │  │
│  └────────────────────────────────────────┘  │
│                                              │
│             Don't have an account?           │
│                    Register                  │
│                                              │
└──────────────────────────────────────────────┘
```

## 7.1 Login UI Rules

- Password is masked by default.
- Show/Hide icon can toggle password visibility.
- Login button is disabled while API request is processing.
- Required fields are marked with `*`.
- Technical system information must not be exposed.
- Authentication errors must use approved business messages.

---

# 8. Login Validation Sequence

The backend validates Login in the following order.

## Step 1 — Email Format

Validate email format and length (per §14.3).

If invalid:

- Login fails.
- Failure Reason = `INVALID_EMAIL` / `EMAIL_TOO_LONG`.
- Create `LOGIN_MANAGEMENT` (USER_ID = NULL).

## Step 2 — Email Exists

Check whether `EMAIL` exists (case-insensitive).

If the Email does not exist:

- Login fails.
- Failure Reason = `USER_NOT_FOUND`.
- Create `LOGIN_MANAGEMENT` (USER_ID = NULL).
- The user-facing message is the same as for a wrong password (anti-enumeration).

## Step 3 — Password

If password is incorrect:

- Login fails.
- Failure Reason = `INVALID_PASSWORD`.
- Create `LOGIN_MANAGEMENT`.

## Step 4 — Status

If password is correct but:

```text
STATUS = DEACTIVE
```

then:

- Login fails.
- Failure Reason = `USER_DEACTIVE`.
- Create `LOGIN_MANAGEMENT`.

## Step 5 — Organization (Part / Group)

If user is ACTIVE, the system checks `organization_master` rows linked to the
user's `knox_id` (the email local part).

### At least one row with `EMPLOYED = 'Y'`

Login succeeds. The response `orgs` list contains the Part/Group of each
employed='Y' row.

### No employed='Y' row

- Login fails.
- No org rows at all → Failure Reason = `ORGANIZATION_NOT_SYNCED`.
- Org rows exist but none employed → Failure Reason = `ORGANIZATION_UNAVAILABLE`.
- Create `LOGIN_MANAGEMENT`.

---

# 9. Login API

> **API prefix:** `/api/v1` — tất cả endpoint trong tài liệu này (vd `POST /api/v1/auth/login`).
> **Response envelope:** `{ "success": boolean, "data": T, "errorCode"?: string, "message"?: string }`.

### Endpoint

`POST /api/v1/auth/login`

### Request

```json
{
  "email": "user@example.com",
  "password": "Password@123"
}
```

### Success Response

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "userName": "Nguyen Nhung",
    "email": "user@example.com",
    "role": "NORMAL_USER",
    "orgs": [ { "part": "Part A", "group": "Group 1" } ],
    "avatarUrl": "/uploads/avatars/1.png",
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi..."
  }
}
```

| Field | Description |
|---|---|
| userId | BIGINT (DB-generated) |
| orgs | List of Part/Group assignments (may be empty) |
| accessToken | JWT, 30 minutes |
| refreshToken | JWT, 7 days (used by `POST /api/v1/auth/refresh`) |

### Error Response

```json
{
  "success": false,
  "errorCode": "INVALID_PASSWORD",
  "message": "Invalid Email or Password."
}
```

---

# 10. Login Management

Table:

`LOGIN_MANAGEMENT`

| Field | Description |
|---|---|
| LOGIN_ID | PK |
| USER_ID | FK (nullable — NULL when the email does not exist or fails format validation) |
| EMAIL | Attempted email |
| LOGIN_IP | Client IP |
| LOGIN_AT | Login attempt time |
| LOGIN_RESULT | SUCCESS / FAILURE |
| FAILURE_REASON | Failure reason |
| APP_ID | `LOGIN` |

## Failure Reasons

| Code | Description |
|---|---|
| INVALID_EMAIL | Invalid email format |
| EMAIL_TOO_LONG | Email longer than 254 characters |
| USER_NOT_FOUND | Email does not exist |
| INVALID_PASSWORD | Incorrect password |
| USER_DEACTIVE | User is deactivated |
| ORGANIZATION_NOT_SYNCED | No organization row linked to the user's knox_id |
| ORGANIZATION_UNAVAILABLE | Organization rows exist but none has `EMPLOYED = 'Y'` |

> **v1.7:** A `USER_NOT_FOUND` record **is** created (with `USER_ID = NULL`).
> The user-facing message is identical to `INVALID_PASSWORD` (anti-enumeration).

---

# 11. Last Successful Login

Last Login is not stored in `USER_MASTER`.

It is derived from:

```sql
SELECT MAX(LOGIN_AT)
FROM LOGIN_MANAGEMENT
WHERE USER_ID = ?
  AND LOGIN_RESULT = 'SUCCESS';
```

---

# 12. User Registration

## 12.1 Registration Overview

Registration is a **3-step, Email-OTP-verified** flow. A user account is only
created after the Email verification OTP is successfully verified.

```text
Step 1: POST /api/v1/auth/register/request-otp
        (email)
        → validate + send EMAIL_VERIFICATION OTP

Step 2: POST /api/v1/auth/register/verify-otp
        (email, otp)
        → verify OTP

Step 3: POST /api/v1/auth/register
        (email, userName, password, confirmPassword)
        → create "user" + user_history (requires verified OTP)
```

> **UI note (v1.7):** The UI presents registration as **2 screens** (form →
> OTP verification). After a successful OTP verification the frontend
> immediately calls Step 3 in the same action — the user never sees a
> separate "complete registration" screen.

## 12.2 Registration Fields

| Field | Required | Rule |
|---|---:|---|
| User Name | Yes | User name |
| Email | Yes | Login ID, unique (case-insensitive), must be OTP-verified |
| Password | Yes | Stored as hash |
| Confirm Password | Yes | Validation only |

`USER_ID` is **not** entered by the user — it is a DB-generated BIGINT.

---

# 13. Registration UI Wireframe

### Step 1 — Create Account form

```text
┌──────────────────────────────────────────────┐
│                                              │
│              Create Account                  │
│                                              │
│  User Name *                                 │
│  ┌────────────────────────────────────────┐  │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Email *                                     │
│  ┌────────────────────────────────────────┐  │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Password *                                  │
│  ┌────────────────────────────────────── 👁 ┐ │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Confirm Password *                          │
│  ┌────────────────────────────────────── 👁 ┐ │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │              SEND OTP                  │  │
│  └────────────────────────────────────────┘  │
│                                              │
│             Already have an account?         │
│                    Log In                   │
│                                              │
└──────────────────────────────────────────────┘
```

### Step 2 — Verify Email (OTP)

```text
┌──────────────────────────────────────────────┐
│                                              │
│            Verify Your Email                 │
│                                              │
│  Enter the 6-digit verification code         │
│  sent to your email address.                 │
│                                              │
│        ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐        │
│        │  │ │  │ │  │ │  │ │  │ │  │        │
│        └──┘ └──┘ └──┘ └──┘ └──┘ └──┘        │
│                                              │
│            Code expires in 04:32             │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │                 VERIFY                 │  │
│  └────────────────────────────────────────┘  │
│                                              │
│                 Resend OTP                   │
│                                              │
└──────────────────────────────────────────────┘
```

> After VERIFY succeeds, the account is created and the user is redirected to Login.

---

# 14. Registration Business Rules

## 14.1 User ID

- DB-generated BIGINT (auto-increment sequence).
- Primary Key.
- **Not** user-entered.
- Immutable after creation.
- Not used as Login ID.

## 14.2 User Name

- Required.
- Stored in `USER_MASTER`.
- Stored in `USER_HISTORY`.
- Not used as Login ID.

## 14.3 Email

- Required.
- Valid email format: `local-part@domain`, exactly one `@`, domain must contain at least one `.`; no spaces or control characters; local-part may only contain letters, digits and `._%+-`.
- Maximum **254 characters** (RFC 5321 maximum address length).
- Must be unique (case-insensitive).
- **Login ID.**
- Must be verified via `EMAIL_VERIFICATION` OTP before the account is created.
- Used for Password Reset OTP.
- Email of a `DEACTIVE` user is still reserved and cannot be reused.

## 14.4 Password

Minimum requirements:

- 8 characters
- Uppercase letter
- Lowercase letter
- Number
- Special character

Password must be hashed before persistence.

Plaintext password must never be:

- Stored
- Returned
- Logged
- Persisted in history

## 14.5 Confirm Password

Must exactly match Password.

---

# 15. Registration Default Values

| Field | Value |
|---|---|
| User ID | DB-generated BIGINT |
| Role | `NORMAL_USER` |
| Status | `ACTIVE` |
| Part / Group | `NULL` / `NULL` |
| Register ID | `SYSTEM00` |
| Created App ID | `LOGIN_REGISTER` |
| Updated By | `SYSTEM00` |

Successful registration creates:

1. One `USER_MASTER`
2. One `USER_HISTORY`

Both must be committed in the same transaction.

---

# 16. Registration API

## 16.1 Step 1 — Request Registration OTP

### Endpoint

`POST /api/v1/auth/register/request-otp`

### Request

```json
{
  "email": "user@example.com"
}
```

### Processing

- Validate email format (per §14.3) and maximum length (254 characters).
- Check email uniqueness (case-insensitive). **If the email is already registered, the request is rejected with `EMAIL_ALREADY_USED`** (the UI validates name/password client-side before sending; the full field validation happens in Step 3).
- Generate `EMAIL_VERIFICATION` OTP, store hash in `OTP_MANAGEMENT` (`USER_ID = NULL`, keyed by `EMAIL`).
- Send OTP email through the internal Email Service (Knox Mail API, §31).

### Response

```json
{
  "success": true
}
```

The response must not reveal whether the email was already registered (anti-enumeration).

### Error Codes

| Code | Description |
|---|---|
| EMAIL_REQUIRED | Email is required |
| INVALID_EMAIL | Invalid Email format |
| EMAIL_TOO_LONG | Email Too Long (> 254 characters) |
| EMAIL_ALREADY_USED | Duplicate Email |

> Step 1 only accepts `email`; name/password validation happens in Step 3. Error code email dùng **bộ `EMAIL_*` chung toàn hệ thống**.

## 16.2 Step 2 — Verify Registration OTP

### Endpoint

`POST /api/v1/auth/register/verify-otp`

### Request

```json
{
  "email": "user@example.com",
  "otp": "483921"
}
```

### Success Response

```json
{
  "success": true
}
```

### Failure

- Incorrect OTP → `OTP_INVALID`.
- Expired OTP → `OTP_EXPIRED`.
- OTP status remains unchanged on failure.

## 16.3 Step 3 — Complete Registration

### Endpoint

`POST /api/v1/auth/register`

### Request

```json
{
  "name": "Nguyen Nhung",
  "email": "user@example.com",
  "password": "Password@123",
  "confirmPassword": "Password@123"
}
```

### Processing

The backend requires a **VERIFIED, unconsumed** `EMAIL_VERIFICATION` OTP for this
email. It then:

1. Validates name (required, max 100 characters), email format/length, password policy, and confirm password match.
2. Re-validates email uniqueness (concurrent safety).
3. Generates `USER_ID` (BIGINT).
4. Hashes the password.
5. Inserts `USER_MASTER`.
6. Inserts `USER_HISTORY`.
7. Marks the verified OTP as consumed.
8. Commits the transaction.

### Success Response

```json
{
  "success": true
}
```

### Error Codes

| Code | Description |
|---|---|
| OTP_NOT_VERIFIED | OTP not verified or expired (registration not allowed) |
| NAME_REQUIRED | User name is required |
| NAME_TOO_LONG | User name is at most 100 characters |
| EMAIL_REQUIRED | Email is required |
| INVALID_EMAIL | Invalid Email format |
| EMAIL_TOO_LONG | Email Too Long (> 254 characters) |
| EMAIL_ALREADY_USED | Duplicate Email |
| PASSWORD_TOO_SHORT | Password shorter than 8 characters |
| PASSWORD_WEAK | Password missing required character types (uppercase / lowercase / number / special character) |
| PASSWORD_MISMATCH | Password Confirmation Mismatch |
| REGISTRATION_FAILED | Registration Transaction Failed |

---

# 17. Registration Transaction

```text
[Step 1] Validate Input
      ↓
Check Email Uniqueness
      ↓
Validate Password
      ↓
Hash check (confirm match)
      ↓
Generate + Hash EMAIL_VERIFICATION OTP
      ↓
Insert OTP_MANAGEMENT (USER_ID = NULL, keyed by EMAIL)
      ↓
Send OTP Email
      ↓
[Step 2] Verify OTP → OTP = VERIFIED
      ↓
[Step 3] Validate verified OTP (not consumed)
      ↓
Re-check Email Uniqueness
      ↓
Generate USER_ID (BIGINT)
      ↓
Hash Password
      ↓
Insert USER_MASTER
      ↓
Insert USER_HISTORY
      ↓
Mark OTP consumed
      ↓
COMMIT
```

Any failure in Step 3:

```text
ROLLBACK
```

No partially registered user is allowed.

---

# 18. Forgot Password

Forgot Password is available from Login.

The user only enters:

**Email**

The system looks up the user by Email (case-insensitive).

Both ACTIVE and DEACTIVE users can use Forgot Password.

---

# 19. Forgot Password UI Wireframe

```text
┌──────────────────────────────────────────────┐
│                                              │
│              Forgot Password                 │
│                                              │
│  Enter your Email to receive a               │
│  verification code.                          │
│                                              │
│  Email *                                     │
│  ┌────────────────────────────────────────┐  │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │                SEND OTP                │  │
│  └────────────────────────────────────────┘  │
│                                              │
│                 Back to Login                │
│                                              │
└──────────────────────────────────────────────┘
```

---

# 20. Forgot Password Request API

### Endpoint

`POST /api/v1/auth/forgot-password/request-otp`

### Request

```json
{
  "email": "user@example.com"
}
```

### Response

The response must not return:

- User ID
- OTP
- OTP hash
- Account information

Example:

```json
{
  "success": true,
  "message": "If the account is eligible, a verification code has been sent to the registered email address."
}
```

The response must prevent account enumeration.

---

# 21. OTP Generation

| Item | Rule |
|---|---|
| Type | `EMAIL_VERIFICATION` (registration) or `PASSWORD_RESET` (forgot password) |
| Length | 6 digits |
| Validity | 5 minutes |
| Invalid attempt limit | None |
| Resend limit | None |
| Storage | Hash only |
| Plaintext OTP | Never persist |
| Valid OTP | Latest active OTP only (per type + target) |

Example:

```text
483921
```

The plaintext OTP exists only temporarily in application memory for email delivery.

---

# 22. OTP Lifecycle

```text
Generate OTP
     ↓
Hash OTP
     ↓
Create OTP_MANAGEMENT
     ↓
Create EMAIL_MANAGEMENT
     ↓
Send Email
     ↓
OTP = ACTIVE
     ↓
User enters OTP
     ↓
Verify
     ↓
OTP = VERIFIED
```

Expiration:

```text
ACTIVE → EXPIRED
```

No `FAILED` OTP status is required.

---

# 23. OTP Resend

When Resend OTP is selected:

1. Invalidate/expire the current active OTP.
2. Generate a new OTP.
3. Hash the new OTP.
4. Create a new OTP record.
5. Send a new email.
6. Only the latest OTP remains valid.

---

# 24. OTP Verification UI

```text
┌──────────────────────────────────────────────┐
│                                              │
│            Verify Your Email                 │
│                                              │
│  Enter the 6-digit verification code         │
│  sent to your email address.                 │
│                                              │
│        ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐        │
│        │  │ │  │ │  │ │  │ │  │ │  │        │
│        └──┘ └──┘ └──┘ └──┘ └──┘ └──┘        │
│                                              │
│            Code expires in 04:32             │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │                 VERIFY                 │  │
│  └────────────────────────────────────────┘  │
│                                              │
│                 Resend OTP                   │
│                                              │
└──────────────────────────────────────────────┘
```

---

# 25. Verify OTP API

### Forgot Password — Endpoint

`POST /api/v1/auth/forgot-password/verify-otp`

### Request

```json
{
  "email": "user@example.com",
  "otp": "483921"
}
```

### Success Response

```json
{
  "verified": true
}
```

Successful OTP verification does **not** change User Status.

For a DEACTIVE user:

```text
DEACTIVE + OTP VERIFIED = DEACTIVE
```

---

# 26. OTP Verification Rules

Backend checks:

1. Target exists (for `EMAIL_VERIFICATION`: email not yet registered; for `PASSWORD_RESET`: user exists).
2. Latest OTP of the matching type exists.
3. OTP status is ACTIVE.
4. Current time <= `EXPIRED_AT`.
5. Submitted OTP hash matches `OTP_HASH`.

Success:

- OTP → `VERIFIED`
- `VERIFIED_AT` populated.

Failure:

- OTP status remains unchanged.
- No FAILED status is created.
- Return invalid/expired OTP message.

A VERIFIED OTP cannot be reused.

---

# 27. Reset Password

Reset Password is available only after successful OTP verification.

## UI Wireframe

```text
┌──────────────────────────────────────────────┐
│                                              │
│              Reset Password                  │
│                                              │
│  New Password *                              │
│  ┌────────────────────────────────────── 👁 ┐ │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Confirm Password *                          │
│  ┌────────────────────────────────────── 👁 ┐ │
│  │                                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Password must contain:                      │
│  • Minimum 8 characters                      │
│  • Uppercase letter                          │
│  • Lowercase letter                          │
│  • Number                                    │
│  • Special character                         │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │             RESET PASSWORD             │  │
│  └────────────────────────────────────────┘  │
│                                              │
└──────────────────────────────────────────────┘
```

---

# 28. Reset Password API

### Endpoint

`POST /api/v1/auth/reset-password`

### Request

```json
{
  "email": "user@example.com",
  "newPassword": "NewPassword@123",
  "confirmPassword": "NewPassword@123"
}
```

### Processing Rules

Backend verifies:

- OTP was successfully verified.
- Verified OTP has not already been consumed.
- New Password satisfies password policy.
- Confirm Password matches.

Then:

1. Hash new password.
2. Update `"user".password` (BCrypt).
3. If current Status is `DEACTIVE`, change Status to `ACTIVE`.
4. Create one `USER_HISTORY` snapshot.
5. Mark the verified OTP as no longer reusable.
6. Commit transaction.

Any failure:

```text
ROLLBACK
```

### Error Codes

| Code | Description |
|---|---|
| OTP_NOT_VERIFIED | OTP not verified or expired (reset not allowed) |
| PASSWORD_TOO_SHORT | Password shorter than 8 characters |
| PASSWORD_WEAK | Password missing required character types (uppercase / lowercase / number / special character) |
| PASSWORD_MISMATCH | Password Confirmation Mismatch |
| PASSWORD_RESET_FAILED | Reset Password Transaction Failed |

> Dùng **cùng bộ error code password granular** với Settings SRS/Spec và Registration (§16.1/§16.3).

## 28.1 Session APIs — Refresh Token & Logout

The app uses stateless JWT: **access token 30 minutes** + **refresh token 7 days**.
The frontend silently refreshes the access token when it expires (or proactively
before expiry) without user interaction.

### Refresh

`POST /api/v1/auth/refresh`

Request:

```json
{ "refreshToken": "eyJhbGciOi..." }
```

Success response: a new access token + rotated refresh token (the old refresh
token is invalidated — rotation).

### Logout

`POST /api/v1/auth/logout`

- Blacklists the current access token (until its natural expiry).
- Revokes the refresh token passed in the request body (optional).

### Logout All

`POST /api/v1/auth/logout-all`

- Blacklists the current access token.
- Revokes **all** refresh tokens of the logged-in user (all devices).

---

# 29. Password Reset Status Transition

## ACTIVE User

```text
ACTIVE
   │
   │ Reset Password Success
   ▼
ACTIVE
```

## DEACTIVE User

```text
DEACTIVE
   │
   │ Forgot Password
   ▼
OTP Verification
   │
   ▼
DEACTIVE
   │
   │ Reset Password Success
   ▼
ACTIVE
```

## Failed Reset

```text
DEACTIVE
   │
   │ Reset Password Failed
   ▼
DEACTIVE
```

### Mandatory Rule

The status must only be changed after the password update is successfully completed.

---

# 30. Password Reset Transaction

For a DEACTIVE user:

```text
BEGIN TRANSACTION
        ↓
Validate verified OTP
        ↓
Validate new password
        ↓
Hash password
        ↓
UPDATE USER_MASTER
   password = new hash
   STATUS = ACTIVE
        ↓
INSERT USER_HISTORY
        ↓
Mark OTP as consumed
        ↓
COMMIT
```

For an ACTIVE user:

```text
BEGIN TRANSACTION
        ↓
Validate verified OTP
        ↓
Validate new password
        ↓
Hash password
        ↓
UPDATE USER_MASTER
   password = new hash
        ↓
INSERT USER_HISTORY
        ↓
Mark OTP as consumed
        ↓
COMMIT
```

---

# 31. OTP Email Delivery

The frontend must never call the email provider directly.

The Authentication Service triggers the internal **Email Service**, which
delivers mail through the **Knox C&C Suite Mail API** (v2.0):

```text
POST {knox.mail.base-url}/mail/api/v2.0/mails/send?userId={sender-knox-id}
Headers: System-ID: {knox.mail.system-id}, Authorization: Bearer {access-token}
Body: { subject, contents (HTML), contentType: HTML, docSecuType: PERSONAL,
        sender: { emailAddress }, recipients: [{ emailAddress, recipientType: TO }] }
```

Configuration (Spring, `application.yml` + env vars):

| Property | Dev / Local | Production |
|---|---|---|
| `knox.mail.base-url` | `https://openapi.stage.samsung.net` | `https://openapi.samsung.net` |
| `knox.mail.sender-email` | `tq.dat1@stage.samsung.com` | `gdcv.cag.ax@samsung.com` |
| `knox.mail.sender-knox-id` | `tq.dat1` | `gdcv.cag.ax` |
| `knox.mail.system-id` / `access-token` | via env vars | via env vars |

When `knox.mail.system-id` is not set, the app falls back to **Log mode**
(OTP written to `backend/otp-dev.log` instead of being sent).

### Subjects

| OTP Type | Subject |
|---|---|
| `EMAIL_VERIFICATION` | "Your registration verification code" |
| `PASSWORD_RESET` | "Your password reset code" |

### Security Rules

Plaintext `otp`:

- Exists only temporarily in memory.
- Is transmitted only over secure internal communication.
- Must never be persisted.
- Must never be logged.
- Must never be returned to frontend.
- Must never be stored in `EMAIL_MANAGEMENT`.

---

# 32. OTP Email Template

### Email Types

- `PASSWORD_RESET_OTP`
- `EMAIL_VERIFICATION_OTP`

### Subjects

| OTP Type | Subject |
|---|---|
| `EMAIL_VERIFICATION` | "Your registration verification code" |
| `PASSWORD_RESET` | "Your password reset code" |

### Email Body

The OTP email is sent as **HTML** (Dark Prism template,
`backend/src/main/resources/email/otp-template.html`) with placeholders:

- `{{OTP}}` — the 6-digit code
- `{{MESSAGE}}` — context line ("Use the verification code below to complete
  your registration." / "Use the verification code below to reset your password.")

The template states the code is valid for 5 minutes and that the user should
ignore the email if they did not request it.

The email must not contain:

- Password
- Password hash
- OTP hash
- Internal database information
- Internal API information
- Technical error information

---

# 33. Forgot Password End-to-End Processing

```text
User
 │
 │ Enter Email
 ▼
Frontend
 │
 │ POST /api/v1/auth/forgot-password/request-otp
 ▼
Authentication Service
 │
 ├── Find "user" by Email
 │
 ├── Generate OTP
 │
 ├── Hash OTP
 │
 ├── Insert OTP_MANAGEMENT
 │
 ├── Insert EMAIL_MANAGEMENT = PENDING
 │
 ▼
Internal Email Service
 │
 │ Send OTP Email
 ▼
Email Provider
 │
 ▼
User receives email
```

The DB transaction must not remain open while waiting for the external email provider.

If email sending fails:

- OTP remains available.
- Email record becomes `FAILED`.
- User can resend OTP.
- Database records are not rolled back solely because email sending failed.

---

# 34. OTP_MANAGEMENT

| Field | Type | Key | Null | Description |
|---|---|---|---|---|
| OTP_ID | VARCHAR | PK | No | OTP identifier |
| USER_ID | BIGINT | FK | **Yes** | User (NULL for `EMAIL_VERIFICATION` pre-registration) |
| OTP_HASH | VARCHAR(255) | | No | Hashed OTP |
| OTP_TYPE | VARCHAR(50) | | No | `EMAIL_VERIFICATION` / `PASSWORD_RESET` |
| EMAIL | VARCHAR(255) | | No | Email (target; used as key when USER_ID is NULL) |
| GENERATED_AT | DATETIME | | No | Generation time |
| EXPIRED_AT | DATETIME | | No | Expiration time |
| VERIFIED_AT | DATETIME | | Yes | Verification time |
| STATUS | VARCHAR(30) | | No | ACTIVE / VERIFIED / EXPIRED |
| CREATED_IP | VARCHAR(45) | | No | Client IP |
| CREATED_AT | DATETIME | | No | Created time |
| UPDATED_IP | VARCHAR(45) | | Yes | Updated IP |
| UPDATED_AT | DATETIME | | No | Updated time |

> The "latest active OTP" rule is scoped per `(OTP_TYPE, EMAIL)` (and `USER_ID` when present).

---

# 35. EMAIL_MANAGEMENT

| Field | Type | Key | Null | Description |
|---|---|---|---|---|
| EMAIL_ID | VARCHAR | PK | No | Email identifier |
| USER_ID | BIGINT | FK | **Yes** | User (NULL for `EMAIL_VERIFICATION` pre-registration) |
| EMAIL | VARCHAR(255) | | No | Destination |
| EMAIL_TYPE | VARCHAR(50) | | No | `PASSWORD_RESET_OTP` / `EMAIL_VERIFICATION_OTP` |
| SUBJECT | VARCHAR(255) | | No | Email subject |
| STATUS | VARCHAR(30) | | No | PENDING / SENT / FAILED |
| SENT_AT | DATETIME | | Yes | Sent time |
| ERROR_CODE | VARCHAR(100) | | Yes | Error code |
| ERROR_MESSAGE | TEXT | | Yes | Error message |
| CREATED_AT | DATETIME | | No | Created time |
| UPDATED_AT | DATETIME | | No | Updated time |

`ERROR_MESSAGE` must never contain plaintext OTP.

---

# 36. USER_MASTER (bảng `"user"`)

| Field | Type | Key | Null | Description |
|---|---|---|---|---|
| id | BIGINT | PK | No | DB-generated (identity), immutable |
| name | VARCHAR(255) | | No | User name |
| gender | VARCHAR(10) | | Yes | Gender |
| email | VARCHAR(255) | UK | No | Email (Login ID, case-insensitive unique) |
| mobile | VARCHAR(50) | | Yes | Mobile |
| password | VARCHAR(255) | | No | BCrypt-hashed password |
| role_id | BIGINT | FK | No | Role (role table: NORMAL_USER / ADMIN_USER) |
| enabled | BOOLEAN | | No | Spring Security flag (default true) |
| avatar_url | VARCHAR(500) | | Yes | Avatar file path |
| status | VARCHAR(30) | | No | ACTIVE / DEACTIVE |
| knox_id | VARCHAR(255) | | No | Knox ID = local-part of email; links `"user"` ↔ `organization_master` |
| register_id | VARCHAR(100) | | Yes | Registration source (null for self-registration) |
| created_at | DATETIME | | No | Created timestamp |
| created_ip | VARCHAR(45) | | No | Created IP |
| created_app_id | VARCHAR(100) | | No | Created application |
| updated_at | DATETIME | | No | Updated timestamp |
| updated_ip | VARCHAR(45) | | Yes | Updated IP |
| updated_app_id | VARCHAR(100) | | Yes | Updated application |
| updated_by | VARCHAR(100) | | No | Updated user/system |
| version | BIGINT | | No | Optimistic lock (`@Version`) |

> `EMAIL` uniqueness must be enforced by a case-insensitive Unique Constraint/Unique Index.

> **Implementation note (v1.7):** The physical table is `"user"` (lowercase, quoted). **Không có** cột `PART`/`GROUP` (đã drop ở V13) — Part/Group được lấy từ bảng `organization_master` (JOIN `"user".knox_id = organization_master.knox_id`, `EMPLOYED = 'Y'`). Cột password là `password` (BCrypt), không phải `password_hash`.

---

# 37. USER_HISTORY (bảng `user_history`)

`USER_HISTORY` stores a complete snapshot of the user data after an actual authentication-related user data change.

| Field |
|---|
| history_id |
| user_id |
| name |
| email |
| role_name |
| status |
| register_id |
| created_at |
| created_ip |
| created_app_id |
| updated_at |
| updated_ip |
| updated_app_id |
| updated_by |
| history_created_at |

> **Note:** `user_history` không có cột password snapshot, không có cột `part`/`group` (Part/Group không thuộc bảng `"user"`).

### History Rules

- Registration → one history record.
- Password Reset → one history record.
- No actual data change → no history record.
- DEACTIVE → ACTIVE caused by successful Password Reset → one history record.

---

# 38. Logical Data Model

```text
                         ┌─────────────────────┐
                         │       "user"        │
                         │---------------------│
                         │ PK id (BIGINT)      │
                         │ name                │
                         │ email (UK)          │
                         │ password (BCrypt)   │
                         │ knox_id             │
                         │ role_id (FK)        │
                         │ status              │
                         └──────────┬──────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             │                      │                      │
             ▼                      ▼                      ▼
   ┌─────────────────┐    ┌──────────────────┐   ┌──────────────────┐
   │  user_history   │    │ LOGIN_MANAGEMENT │   │ OTP_MANAGEMENT   │
   └─────────────────┘    └──────────────────┘   └─────────┬────────┘
                                                           │
                                                           ▼
                                                  ┌──────────────────┐
                                                  │ EMAIL_MANAGEMENT │
                                                  └──────────────────┘
```

---

# 39. Relationship

| Parent | Child | Cardinality |
|---|---|---|
| USER_MASTER | USER_HISTORY | 1:N |
| USER_MASTER | LOGIN_MANAGEMENT | 1:N |
| USER_MASTER | OTP_MANAGEMENT | 1:N (OTP_MANAGEMENT.USER_ID nullable for pre-registration) |
| USER_MASTER | EMAIL_MANAGEMENT | 1:N (EMAIL_MANAGEMENT.USER_ID nullable for pre-registration) |

---

# 40. Table Classification

| Table | Type | Purpose |
|---|---|---|
| USER_MASTER | Master | Current user state |
| USER_HISTORY | History | User snapshots |
| LOGIN_MANAGEMENT | Transaction | Login attempts |
| OTP_MANAGEMENT | Transaction | OTP lifecycle |
| EMAIL_MANAGEMENT | Transaction | Email lifecycle |

---

# 41. Password Security

Use an approved secure password hashing algorithm according to the project's security standard, such as Argon2id or bcrypt.

Never store:

```text
PASSWORD
```

Store only:

```text
password  (BCrypt hash)
```

Password must never be:

- Returned by API
- Logged
- Stored as plaintext
- Included in email
- Exposed in UI

---

# 42. OTP Security

Never store:

```text
OTP = 483921
```

Store:

```text
OTP_HASH
```

Plaintext OTP exists only temporarily in application memory for email delivery.

---

# 43. API Traceability

| API | "user" | user_history | LOGIN | OTP | EMAIL |
|---|---|---|---|---|---|
| POST /api/v1/auth/register/request-otp | R | - | - | C | C/U |
| POST /api/v1/auth/register/verify-otp | - | - | - | U | - |
| POST /api/v1/auth/register | C | C | - | U | - |
| POST /api/v1/auth/login | R | R | C | - | - |
| POST /api/v1/auth/forgot-password/request-otp | R | - | - | C | C/U |
| POST /api/v1/auth/forgot-password/verify-otp | R | - | - | U | - |
| POST /api/v1/auth/reset-password | U | C | - | U | - |
| POST /api/v1/auth/refresh | R | - | - | - | - |
| POST /api/v1/auth/logout | - | - | - | - | - |
| POST /api/v1/auth/logout-all | R | - | - | - | - |

> **Resend OTP** has no dedicated endpoint — the UI re-calls `request-otp` (same
> endpoint as the initial send), which invalidates the previous ACTIVE OTP.
> Mail delivery is internal (Email Service → Knox Mail API, §31); there is no
> `POST /internal/email/otp` HTTP endpoint.

---

# 44. Authentication Error Handling

Do not expose:

- SQL errors
- Stack traces
- Database structures
- Internal server paths
- Email provider credentials
- OTP values
- Password values
- Password hashes

Example:

```json
{
  "success": false,
  "errorCode": "EMAIL_ALREADY_USED",
  "message": "Email already exists."
}
```

## 44.1 Error Message Catalog

All user-facing error messages are in **English** and identified by a stable ID.
The frontend maps `errorCode` → message ID.

| ID | Message | Error Code(s) | Where |
|---|---|---|---|
| ERR-AUTH-001 | "Invalid Email or Password." | `INVALID_PASSWORD`, `USER_DEACTIVE` (login — same message for both to avoid account enumeration) | Login |
| ERR-AUTH-002 | "Email is required." | `EMAIL_REQUIRED` | Registration, Forgot Password, Reset |
| ERR-AUTH-003 | "Invalid Email format." | `INVALID_EMAIL` | Registration, Forgot Password, Reset |
| ERR-AUTH-004 | "Email is too long (maximum 254 characters)." | `EMAIL_TOO_LONG` | Registration, Forgot Password, Reset |
| ERR-AUTH-005 | "Email already exists." | `EMAIL_ALREADY_USED` | Registration (Step 1 & Step 3) |
| ERR-AUTH-006 | "Password must be at least 8 characters." | `PASSWORD_TOO_SHORT` | Registration, Reset Password |
| ERR-AUTH-007 | "Password must contain uppercase, lowercase, number and special character." | `PASSWORD_WEAK` | Registration, Reset Password |
| ERR-AUTH-008 | "Password confirmation mismatch." | `PASSWORD_MISMATCH` | Registration, Reset Password |
| ERR-AUTH-009 | "User name is required." | `NAME_REQUIRED` | Registration (Step 3) |
| ERR-AUTH-010 | "OTP not verified or expired. Please verify your email before continuing." | `OTP_NOT_VERIFIED` | Registration (Step 3), Reset Password |
| ERR-AUTH-011 | "Registration failed. Please try again." | `REGISTRATION_FAILED` | Registration (Step 3) |
| ERR-AUTH-012 | "Password reset failed. Please try again." | `PASSWORD_RESET_FAILED` | Reset Password |
| ERR-AUTH-013 | "The code is incorrect." | `OTP_INVALID` | Verify OTP (Registration / Forgot Password) |
| ERR-AUTH-014 | "The code has expired. Please request a new one." | `OTP_EXPIRED` | Verify OTP (Registration / Forgot Password) |
| ERR-AUTH-015 | "Unable to send the verification code. Please try again." | (email service failure) | Send OTP |
| ERR-AUTH-016 | "Your account has been deactivated. Please use Forgot Password to recover your account." | `USER_DEACTIVE` (displayed only when the user explicitly checks account status) | Login (optional detail) |
| ERR-AUTH-017 | "Your organization information is not available. Please contact your administrator." | `ORGANIZATION_NOT_SYNCED`, `ORGANIZATION_UNAVAILABLE` | Login |
| ERR-AUTH-018 | "Password updated successfully." | — (success) | Reset Password |
| ERR-AUTH-019 | "If the account is eligible, a verification code has been sent to the registered email address." | — (anti-enumeration response) | Forgot Password |
| ERR-AUTH-020 | "System error. Please try again later." | (HTTP 500) | All authentication APIs |

> **Anti-enumeration rule:** Login and Forgot Password responses must not reveal whether an email/account exists. `ERR-AUTH-001` is used for both wrong password and deactivated user at login. **Exception (v1.7):** Registration Step 1 returns `EMAIL_ALREADY_USED` when the email is already registered (the UI shows it inline before the user waits for an OTP).

---

# 45. Acceptance Criteria — Registration

### AC-REG-01

Given valid registration data and a successfully verified Email OTP, when the user completes registration, USER_MASTER and USER_HISTORY are created in one transaction.

### AC-REG-02

Given an existing Email (case-insensitive), registration is rejected.

### AC-REG-03

Given an invalid password, registration is rejected.

### AC-REG-04

Given mismatched Password and Confirm Password, registration is rejected.

### AC-REG-05

Given missing User Name, registration is rejected.

### AC-REG-06

Registration cannot complete without a successfully verified `EMAIL_VERIFICATION` OTP.

### AC-REG-07

`USER_ID` is a DB-generated BIGINT and is not entered by the user.

### AC-REG-08

After successful registration:

```text
ROLE = NORMAL_USER
STATUS = ACTIVE
PART = NULL
GROUP = NULL
```

---

# 46. Acceptance Criteria — Login

### AC-LOGIN-01

Valid Email + valid Password + ACTIVE + at least one `organization_master` row with `EMPLOYED = 'Y'` for the user's `knox_id` → Login succeeds.

### AC-LOGIN-02

Invalid password → Login fails and LOGIN_MANAGEMENT is created.

### AC-LOGIN-03

DEACTIVE user → Login fails.

### AC-LOGIN-04

Unknown Email → Login fails; a LOGIN_MANAGEMENT record is created with `USER_ID = NULL` and `FAILURE_REASON = USER_NOT_FOUND`. The user-facing message is identical to a wrong password (anti-enumeration).

### AC-LOGIN-05

No `organization_master` row linked to the user's `knox_id` → `ORGANIZATION_NOT_SYNCED`.

### AC-LOGIN-06

`organization_master` rows exist for the user's `knox_id` but none has `EMPLOYED = 'Y'` → `ORGANIZATION_UNAVAILABLE`.

### AC-LOGIN-07

After a successful login, the user is redirected to the Home screen.

### AC-LOGIN-08

After a failed login, the user remains on the Login screen.

---

# 47. Acceptance Criteria — Forgot Password

### AC-FORGOT-01

Given a valid Email, the system locates the user and sends the reset OTP to that Email.

### AC-FORGOT-02

Both ACTIVE and DEACTIVE users can request OTP.

### AC-FORGOT-03

A 6-digit OTP is generated.

### AC-FORGOT-04

OTP is valid for 5 minutes.

### AC-FORGOT-05

Plaintext OTP is never stored.

### AC-FORGOT-06

OTP email is sent through the internal Email Service.

### AC-FORGOT-07

Frontend never directly calls the email provider.

### AC-FORGOT-08

Resend OTP invalidates the previous OTP.

### AC-FORGOT-09

Only the latest OTP is valid.

---

# 48. Acceptance Criteria — OTP

### AC-OTP-01

Correct active OTP → verification succeeds.

### AC-OTP-02

Incorrect OTP → verification fails.

### AC-OTP-03

Expired OTP → verification fails.

### AC-OTP-04

Verified OTP cannot be reused.

### AC-OTP-05

Successful verification changes OTP status to VERIFIED.

### AC-OTP-06

Successful OTP verification does not change DEACTIVE → ACTIVE.

### AC-OTP-07

`EMAIL_VERIFICATION` OTP is stored with `USER_ID = NULL` and keyed by Email.

---

# 49. Acceptance Criteria — Reset Password

### AC-RESET-01

User cannot access Reset Password without successful OTP verification.

### AC-RESET-02

Valid new password + matching confirmation → reset succeeds.

### AC-RESET-03

Invalid password policy → reset fails.

### AC-RESET-04

Password mismatch → reset fails.

### AC-RESET-05

Successful reset updates `"user".password` (BCrypt hash).

### AC-RESET-06

Successful reset creates exactly one `USER_HISTORY` record.

### AC-RESET-07

ACTIVE user remains ACTIVE after successful Password Reset.

### AC-RESET-08

DEACTIVE user changes to ACTIVE after successful Password Reset.

### AC-RESET-09

If Password Reset fails, DEACTIVE user remains DEACTIVE.

### AC-RESET-10

DEACTIVE → ACTIVE caused by successful Password Reset is recorded in USER_HISTORY.

### AC-RESET-11

Verified OTP cannot be reused.

---

# 50. Frontend UX Requirements

- After a successful login, the user is redirected to the Home screen.
- After a failed login, the user remains on the Login screen with the error message.
- Login uses Email (not User ID) as the identifier.
- Required fields use `*`.
- Password fields are masked by default.
- Show/Hide password control is available.
- Submit buttons are disabled during API processing.
- Business-level API errors are displayed.
- Technical exceptions are not exposed.
- Registration requires a 3-step flow: form → Email OTP verify → account created.
- OTP input accepts numeric characters only.
- OTP contains exactly 6 digits.
- OTP expiry timer is displayed.
- Resend OTP is available.
- Reset Password is accessible only after OTP verification.
- Successful Password Reset displays a success message.
- Successful Password Reset redirects to Login.
- DEACTIVE user sees the same Forgot Password flow as ACTIVE user.
- DEACTIVE user is not activated at OTP verification.
- DEACTIVE user becomes ACTIVE only after successful Password Reset.

---

# 51. DEV Security Checklist

- [ ] Password is never stored as plaintext.
- [ ] Password is never returned by API.
- [ ] Password is never logged.
- [ ] OTP is never stored as plaintext.
- [ ] OTP is never logged.
- [ ] OTP is never returned by API.
- [ ] Frontend never calls email provider directly.
- [ ] Internal Email API uses secure communication.
- [ ] DEACTIVE users cannot Login.
- [ ] DEACTIVE users can use Forgot Password.
- [ ] OTP verification alone does not activate DEACTIVE users.
- [ ] Successful Password Reset activates DEACTIVE users.
- [ ] Failed Password Reset does not activate DEACTIVE users.
- [ ] Verified OTP cannot be reused.
- [ ] Only latest OTP is valid.
- [ ] Registration requires verified EMAIL_VERIFICATION OTP before account creation.
- [ ] Forgot Password does not expose account existence.
- [ ] Sensitive API data is protected.
- [ ] SQL/database errors are not exposed.
- [ ] HTTPS is required.
- [ ] Approved secure password hashing is used.

---

# 52. DEV Database Checklist

- [ ] USER_MASTER created.
- [ ] USER_HISTORY created.
- [ ] LOGIN_MANAGEMENT created.
- [ ] OTP_MANAGEMENT created.
- [ ] EMAIL_MANAGEMENT created.
- [ ] USER_ID BIGINT PK configured (DB-generated, `BIGSERIAL`).
- [ ] EMAIL case-insensitive unique constraint configured.
- [ ] KNOX_ID present on `"user"` (derived from the email local part) — used to link `user` ↔ `organization_master` for the login org check (V13).
- [ ] `user.part` / `user.group_name` removed (V13) — Part/Group come from `organization_master` rows.
- [ ] OTP_MANAGEMENT.USER_ID nullable for EMAIL_VERIFICATION.
- [ ] EMAIL_MANAGEMENT.USER_ID nullable for EMAIL_VERIFICATION.
- [ ] Foreign keys configured.
- [ ] OTP_HASH stored instead of plaintext OTP.
- [ ] `password` (BCrypt) stored instead of plaintext password.
- [ ] Created/Updated audit fields implemented.
- [ ] Registration creates USER_MASTER + USER_HISTORY atomically.
- [ ] Password Reset creates USER_HISTORY.
- [ ] DEACTIVE → ACTIVE transition is transactional.

---

# 53. DEV API Checklist

- [ ] `POST /api/v1/auth/register/request-otp`
- [ ] `POST /api/v1/auth/register/verify-otp`
- [ ] `POST /api/v1/auth/register`
- [ ] `POST /api/v1/auth/login`
- [ ] `POST /api/v1/auth/forgot-password/request-otp`
- [ ] `POST /api/v1/auth/forgot-password/verify-otp`
- [ ] `POST /api/v1/auth/reset-password`
- [ ] `POST /api/v1/auth/refresh`
- [ ] `POST /api/v1/auth/logout`
- [ ] `POST /api/v1/auth/logout-all`

---

# 54. UX/UI Handoff Checklist

UX/UI must provide:

- [ ] Login screen (Email + Password)
- [ ] Register screen (Step 1: form)
- [ ] Register OTP Verification screen (Step 2)
- [ ] Forgot Password screen (Email)
- [ ] OTP Verification screen
- [ ] Reset Password screen
- [ ] Validation messages
- [ ] API error states
- [ ] Loading states
- [ ] OTP expired state
- [ ] OTP invalid state
- [ ] Resend OTP state
- [ ] Password visibility state
- [ ] Password policy display
- [ ] Password Reset success state
- [ ] DEACTIVE user Password Reset flow

---

# 55. QA Test Scope

## Registration

- Valid registration (with verified Email OTP)
- Registration blocked without verified OTP
- Duplicate Email (case-insensitive)
- Invalid Email
- Invalid Password
- Password mismatch
- Missing User Name
- USER_ID is DB-generated BIGINT
- Transaction rollback

## Login

- Valid login (by Email)
- Invalid password
- Unknown Email
- DEACTIVE user
- No org row for knox_id (ORGANIZATION_NOT_SYNCED)
- Org rows exist but none employed='Y' (ORGANIZATION_UNAVAILABLE)

## Forgot Password

- ACTIVE user
- DEACTIVE user
- Unknown Email
- OTP generation
- OTP email
- OTP expiry
- Invalid OTP
- Resend OTP
- Latest OTP rule

## Reset Password

- ACTIVE user reset
- DEACTIVE user reset
- Successful DEACTIVE → ACTIVE transition
- Invalid password
- Password mismatch
- Reset without OTP
- Reuse verified OTP
- Transaction rollback
- Failed reset keeps DEACTIVE status

---

# 56. Requirement-to-Data Traceability

| Requirement | Main Table |
|---|---|
| User account | `"user"` |
| User ID (BIGINT, DB-generated) | `"user".id` |
| User Name | `"user".name` / `user_history.name` |
| Login identifier (Email) | `"user".email` |
| Knox ID (email local part) | `"user".knox_id` |
| Authentication | `"user"` |
| Password | `"user".password` (BCrypt) |
| User registration history | `user_history` |
| Login attempt | LOGIN_MANAGEMENT |
| Last successful login | LOGIN_MANAGEMENT |
| OTP lifecycle | OTP_MANAGEMENT |
| OTP email | EMAIL_MANAGEMENT |
| Email verification (registration) | OTP_MANAGEMENT (EMAIL_VERIFICATION) |
| Part/Group used during authentication | ORGANIZATION_MASTER (via KNOX_ID) |
| Role used during authentication | USER_MASTER / USER_HISTORY |
| Status used during authentication | USER_MASTER / USER_HISTORY |
| Registration audit | USER_MASTER / USER_HISTORY |
| Password Reset | USER_MASTER / USER_HISTORY |
| DEACTIVE → ACTIVE by Password Reset | USER_MASTER / USER_HISTORY |

---

# 57. Final Authentication State Model

```text
                    REGISTER
                       │
                       ▼
                ┌─────────────┐
                │    ACTIVE   │
                └──────┬──────┘
                       │
                       │ Forgot Password
                       ▼
                ┌─────────────┐
                │ OTP Request │
                └──────┬──────┘
                       │
                       ▼
                ┌─────────────┐
                │ OTP Verify  │
                └──────┬──────┘
                       │
                       ▼
                ┌─────────────┐
                │    RESET    │
                │   PASSWORD  │
                └──────┬──────┘
                       │
                       ▼
                    ACTIVE
```

For a DEACTIVE user:

```text
DEACTIVE
    │
    │ Forgot Password
    ▼
OTP Request
    │
    ▼
OTP Verification
    │
    ▼
DEACTIVE
    │
    │ Reset Password Success
    ▼
ACTIVE
```

---

# 58. Final Authentication Navigation

```text
LOGIN (Email + Password)
 │
 ├── Login Success
 │      │
 │      └── Home
 │
 ├── Register
 │      │
 │      └── Step 1: Form (Name/Email/Password) → Send OTP
 │              │
 │              └── Step 2: Verify Email OTP
 │                      │
 │                      └── Step 3: Account Created → Login
 │
 └── Forgot Password
        │
        └── Enter Email
               │
               └── OTP Verification
                      │
                      └── Reset Password
                             │
                             └── Reset Success
                                    │
                                    └── Login
```

---

# 59. Mandatory Business Rules

1. **Email is the Login ID (case-insensitive).**
2. **User ID is a DB-generated BIGINT, not user-entered, and is not the Login ID.**
3. **Knox ID is derived from the email local part** (`knox_id = SPLIT_PART(email, '@', 1)`) and is used to link `user` ↔ `organization_master` for the login org check. It is not user-entered and is not the Login ID.
4. **Email is unique (case-insensitive) and must be OTP-verified at registration.**
5. **User Name is stored but is not the Login ID.**
6. **Password is always stored as a hash.**
7. **OTP is always stored as a hash.**
8. **OTP is valid for 5 minutes.**
9. **Only the latest OTP is valid (per type + target).**
10. **Verified OTP cannot be reused.**
11. **Frontend never calls the email provider directly.**
12. **OTP email is handled through the Internal Email Service (Knox C&C Suite Mail API; Log mode fallback when no Knox credentials).**
13. **Registration requires a verified EMAIL_VERIFICATION OTP before account creation.**
14. **Password Reset requires successful OTP verification.**
15. **ACTIVE user remains ACTIVE after Password Reset.**
16. **DEACTIVE user becomes ACTIVE after successful Password Reset.**
17. **OTP verification alone does not activate a DEACTIVE user.**
18. **Failed Password Reset does not activate a DEACTIVE user.**
19. **DEACTIVE → ACTIVE must be committed in the same transaction as the successful password update.**
20. **DEACTIVE → ACTIVE must create a USER_HISTORY snapshot.**
21. **Sensitive information must never be exposed through logs or API responses.**
22. **Login and Forgot Password responses must not expose whether an account exists. (Registration Step 1 is the exception — it returns `EMAIL_ALREADY_USED` for a taken email.)**
23. **Login succeeds only when at least one `organization_master` row linked to the user's `knox_id` has `EMPLOYED = 'Y'`.**

---

# 60. Version History

| Version | Status | Change |
|---|---|---|
| 1.0 | Baseline | Create Login SRs |
| 1.1 | Updated | Added Home screen redirect after successful login |
| 1.2 | Updated | Replaced `ORGANIZATION` column with `PART` + `GROUP` (both nullable) in USER_MASTER / USER_HISTORY; login succeeds when at least one of PART/GROUP is populated; both NULL = Organization NULL |
| 1.3 | Updated | `USER_ID` now DB-generated (PK, not user-entered); `EMAIL` becomes the Login ID (unique, case-insensitive); registration now requires a verified `EMAIL_VERIFICATION` OTP (3-step flow reusing `OTP_MANAGEMENT`); login and forgot password use Email |
| 1.4 | Updated | Added specific Email format validation rules (§14.3): format standard (`local-part@domain`, one `@`, domain with at least one `.`), max **254 characters** (RFC 5321); new error code (Email Too Long) in §16.1/§16.3 |
| 1.5 | Updated | Replaced `AUTH-REG-XXX` error codes with the system-wide SCREAMING_SNAKE convention (aligned with User Mgmt SRS Create User & Settings SRS/Spec): email → shared `EMAIL_REQUIRED` / `INVALID_EMAIL` / `EMAIL_TOO_LONG` / `EMAIL_ALREADY_USED`; others → `OTP_NOT_VERIFIED`, `PASSWORD_MISMATCH`, `REGISTRATION_FAILED` (§16.1, §16.3, §44) |
| 1.6 | Updated | Password error codes aligned to the granular Settings SRS/Spec set: `PASSWORD_POLICY_INVALID` → `PASSWORD_TOO_SHORT` + `PASSWORD_WEAK` (§16.1, §16.3); added Reset Password error codes (§28: `OTP_NOT_VERIFIED`, `PASSWORD_TOO_SHORT`, `PASSWORD_WEAK`, `PASSWORD_MISMATCH`, `PASSWORD_RESET_FAILED`) |
| 1.7 | Updated | Aligned with implementation: `USER_ID` is a **BIGINT** (`BIGSERIAL`), not UUID; `KNOX_ID` re-introduced on `"user"` (derived from email local part) and the login org check now uses `organization_master` rows with `EMPLOYED='Y'` via `knox_id` (replacing the `PART`/`GROUP` columns, dropped in V13); login step 4 rewritten (§8); `LOGIN_MANAGEMENT` now records `USER_NOT_FOUND`/`INVALID_EMAIL`/`EMAIL_TOO_LONG` with nullable `USER_ID` and an `EMAIL` column (§10, AC-LOGIN-04/05/06); login response carries `orgs[]` + JWT access/refresh tokens (§9); added `POST /api/v1/auth/refresh`, `/auth/logout`, `/auth/logout-all` (§28.1, §43, §53); reset-password endpoint is `POST /api/v1/auth/reset-password` (§28); OTP email delivered via **Knox C&C Suite Mail API** with Log-mode fallback (§31, §32); registration Step 1 request is email-only and returns `EMAIL_ALREADY_USED` for a taken email (§16.1, §44); registration UI is 2 screens (form → OTP) with Step 3 auto-called after verify (§12.1) |
| 1.8 | Updated | Aligned with implementation: (1) **API prefix `/api/v1`** added to all auth endpoints (§9, §16, §20, §28, §43, §53) with response envelope note; (2) DB schema sections aligned with actual tables — §36 USER_MASTER → `"user"` (actual columns `id`, `name`, `password` BCrypt, `knox_id`, `avatar_url`, `version`; **không có** `PART`/`GROUP`); §37 USER_HISTORY → `user_history` (không có password snapshot, không có part/group); §38 logical data model, §41 password column name, §56 traceability updated to physical table/column names |
