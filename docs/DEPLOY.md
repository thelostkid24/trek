# Deploying Sahyātri to AWS (Mumbai, ap-south-1)

A console runbook for the first production stack. Do it by hand once so you know what each piece does; move it to Terraform/CDK once it's stable. Everything is in **ap-south-1** except the two things AWS requires in **us-east-1** (the CloudFront certificate and the WAF web ACL).

```
Browser ─▶ Route 53 ─▶ CloudFront (TLS, WAF, CloudFront Function)
                         ├─ /*      ─▶ S3 "web" bucket (React build, private, OAC)
                         └─ /api/*  ─▶ ALB (only CloudFront can reach it) ─▶ ECS Fargate: 1 × backend container
                                                                              ├─ RDS PostgreSQL 16 (private)
                                                                              ├─ S3 "uploads" bucket
                                                                              ├─ SES (email) · MSG91 (SMS) · Razorpay
                                                                              └─ Secrets Manager
Razorpay webhooks ─▶ https://<domain>/api/webhooks/razorpay
```

Same origin: the SPA and the API share `https://<domain>`, so there's no CORS in production and the refresh cookie is first-party.

Throughout, `<domain>` is your apex domain (e.g. `sahyatri.com`).

---

## 0. Start the slow things first (they take days)
| What | Where | Why |
|---|---|---|
| Razorpay KYC + website review | Razorpay dashboard → Account & Settings | Live keys. Needs PAN, bank account, GST if registered, a sample invoice and live Terms / Privacy / Refunds / Contact / About pages on your domain |
| TRAI DLT registration | Any DLT portal (Jio, Vodafone Idea, Airtel, …), then link it in MSG91 | Indian SMS won't deliver without a registered entity, sender ID (header) and OTP template |
| SES production access | SES console → Account dashboard → Request production access | New accounts can only email verified addresses |
| AWS Activate credits | aws.amazon.com/activate | Free credits for startups |

Until DLT is approved, run with `SMS_PROVIDER=log` and tell people to sign in with Google or email. Phone OTP won't deliver.

## 1. AWS account hygiene (30 min)
1. Enable **MFA on the root user**, then stop using root. Create a user in **IAM Identity Center** with `AdministratorAccess` for daily work.
2. **Billing → Budgets**: a monthly cost budget with alerts at $50 and $100.
3. Set the console region to **Asia Pacific (Mumbai) ap-south-1**.

## 2. Domain and DNS
1. **Route 53 → Registered domains → Register** `<domain>` (a `.com` is about $14/yr). Route 53 creates the hosted zone.
   - If you buy elsewhere (e.g. a `.in` from a registrar Route 53 doesn't support), create a **hosted zone** in Route 53 and set the registrar's nameservers to the four `NS` values.
2. Everything below adds records to this hosted zone.

## 3. Certificates (ACM)
1. **us-east-1** → ACM → Request a public certificate for `<domain>` and `www.<domain>` (this one is for CloudFront).
2. **ap-south-1** → ACM → Request a certificate for `<domain>`, `www.<domain>` and `origin.<domain>` (this one is for the ALB).
3. For each, click **Create records in Route 53** and wait for *Issued*.

## 4. Network and security groups
Use the **default VPC** to start. Its subnets are public, so Fargate tasks get a public IP and reach ECR, SES, Razorpay and MSG91 without a NAT Gateway, which would add about $35/mo. The security groups below keep everything else closed.

| Security group | Inbound |
|---|---|
| `sahyatri-alb` | 443 from the managed prefix list `com.amazonaws.global.cloudfront.origin-facing` |
| `sahyatri-app` | 8081 from `sahyatri-alb` |
| `sahyatri-db` | 5432 from `sahyatri-app` |

## 5. Database (RDS)
RDS → Create database:
- **Engine:** PostgreSQL 16.x. **Template:** Free tier or Dev/Test. **Instance:** `db.t4g.micro`, 20 GB gp3 (storage autoscaling up to 100 GB).
- **Credentials:** master user `sahyatri`, *Manage master credentials in AWS Secrets Manager*.
- **Connectivity:** default VPC, **Public access: No**, security group `sahyatri-db`.
- **Initial database name:** `sahyatri`.
- **Backups:** automated backups 7 days (this gives point-in-time restore). Turn on **deletion protection**.

RDS for PostgreSQL 16 enforces SSL, so the JDBC URL ends in `?sslmode=require`.

## 6. S3 buckets
Create two buckets in ap-south-1, both with **Block all public access** on:
- `sahyatri-web-<random>`: the React build. CloudFront reads it through OAC (step 11 adds the bucket policy).
- `sahyatri-uploads-<random>`: avatars. Only the backend task role touches it.

## 7. Email (SES, ap-south-1)
1. SES → Identities → Create identity → **Domain** `<domain>` with **Easy DKIM**. Publish the records to Route 53 (one click).
2. Set a **custom MAIL FROM domain** (e.g. `mail.<domain>`) and publish its MX/SPF records.
3. Add a DMARC TXT record: `_dmarc.<domain>` = `v=DMARC1; p=quarantine; rua=mailto:dmarc@<domain>`.
4. Once production access is granted, `MAIL_FROM=Sahyatri <no-reply@<domain>>` works.

## 8. Secrets (Secrets Manager, ap-south-1)
Create one secret `sahyatri/prod` (type *Other*) with these keys:

| Key | Value |
|---|---|
| `JWT_SECRET` | output of `openssl rand -base64 48` |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | test keys first, live after activation |
| `RAZORPAY_WEBHOOK_SECRET` | the secret you type when registering the webhook (step 14) |
| `MSG91_AUTH_KEY` | once DLT is done |

The DB password stays in the RDS-managed secret (`rds!db-…`, key `password`).

## 9. Container registry
ECR → Create repository `sahyatri-backend` (private, scan on push). Push the first image by hand:

```bash
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.ap-south-1.amazonaws.com
docker build --platform linux/amd64 -t <account>.dkr.ecr.ap-south-1.amazonaws.com/sahyatri-backend:first backend
docker push <account>.dkr.ecr.ap-south-1.amazonaws.com/sahyatri-backend:first
```

## 10. Backend on ECS Fargate
### IAM roles
- **`sahyatri-task-execution`** (used by ECS to start the container): `AmazonECSTaskExecutionRolePolicy`, plus `secretsmanager:GetSecretValue` on `sahyatri/prod*` and the `rds!db-*` secret.
- **`sahyatri-task`** (used by the app itself):
  - `s3:GetObject`, `s3:PutObject` and `s3:DeleteObject` on `arn:aws:s3:::sahyatri-uploads-<random>/*`.
  - `ses:SendEmail` on the SES identity.

### Task definition `sahyatri-backend`
- **Launch type:** Fargate, Linux/X86_64, **0.5 vCPU / 1 GB**. Both roles as above.
- **Container `backend`:** image `…/sahyatri-backend:first`, port **8081**, log driver `awslogs` (group `/ecs/sahyatri-backend`, retention 30 days).
- **Environment variables:**

  | Name | Value |
  |---|---|
  | `DB_URL` | `jdbc:postgresql://<rds-endpoint>:5432/sahyatri?sslmode=require` |
  | `DB_USER` | `sahyatri` |
  | `DB_POOL_SIZE` | `10` |
  | `PUBLIC_BASE_URL`, `FRONTEND_BASE_URL` | `https://<domain>` |
  | `CORS_ALLOWED_ORIGINS` | `https://<domain>` |
  | `AUTH_COOKIE_SECURE` | `true` |
  | `GOOGLE_CLIENT_ID` | your OAuth client id |
  | `STORAGE_TYPE` | `s3` |
  | `S3_UPLOADS_BUCKET` | `sahyatri-uploads-<random>` |
  | `AWS_REGION` | `ap-south-1` |
  | `MAIL_PROVIDER` | `ses` |
  | `MAIL_FROM` | `Sahyatri <no-reply@<domain>>` |
  | `SMS_PROVIDER` | `log` until DLT is done, then `msg91` |
  | `MSG91_OTP_TEMPLATE_ID` | from MSG91 |
  | `RATE_LIMIT_CLIENT_IP_HEADER` | `CloudFront-Viewer-Address` |
  | `ADMIN_EMAILS` | your email |
  | `GUIDE_SHARE_BPS` | `7000` |

- **Secrets** (valueFrom): `JWT_SECRET`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` and `MSG91_AUTH_KEY` from `sahyatri/prod`; `DB_PASSWORD` from the RDS secret's `password` key.

### Load balancer
EC2 → Load balancers → **Application**, internet-facing, default VPC (at least 2 AZs), SG `sahyatri-alb`.
- **Target group `sahyatri-backend`:**
  - type **IP**, HTTP 8081.
  - health check `/api/public/health`, healthy threshold 2, interval 15 s.
  - deregistration delay 30 s.
- **Listener HTTPS:443:** cert from step 3.2. The **default action returns a fixed 403.**
- **Listener rule:** if HTTP header `X-Origin-Verify` equals `<long random string>` → forward to `sahyatri-backend`. Only CloudFront knows the string (it adds it in step 11).

### Cluster and service
- ECS → Create cluster `sahyatri` (**Fargate only**). It's just a named group; there are no servers to manage.
- Create service:
  - task `sahyatri-backend`, **desired tasks 1**.
  - deployment: min 100% / max 200%, **circuit breaker with rollback** on.
  - network: default VPC subnets, SG `sahyatri-app`, **public IP on**.
  - load balancer: the ALB and target group above, health-check grace period 120 s. Spring plus Flyway needs time to start.
- First start runs the Flyway migrations V1…Vn against the empty database. Watch it in CloudWatch Logs.

Route 53: an `A` alias record `origin.<domain>` → the ALB.

## 11. CloudFront (the front door)
### CloudFront Function `sahyatri-spa` (viewer request, default behaviour only)
It redirects `www` to the apex and serves `index.html` for SPA routes. Don't use "custom error responses" for SPA routing: they would also turn the API's JSON 404s into `index.html`.

```js
function handler(event) {
  var req = event.request;
  var host = req.headers.host && req.headers.host.value;
  if (host && host.indexOf('www.') === 0) {
    return { statusCode: 301, statusDescription: 'Moved Permanently',
             headers: { location: { value: 'https://' + host.slice(4) + req.uri } } };
  }
  if (req.uri.indexOf('.') === -1) { req.uri = '/index.html'; }  // /treks/x, /account/bookings/y, …
  return req;
}
```

### Distribution
- **Origins:**
  - S3 `sahyatri-web-<random>` with **Origin Access Control**. Let CloudFront update the bucket policy.
  - `origin.<domain>`: HTTPS only, custom header `X-Origin-Verify: <same random string>`.
- **Default behaviour `/*` → S3:**
  - Redirect HTTP to HTTPS.
  - Cache policy `CachingOptimized`.
  - Function `sahyatri-spa` on viewer request.
- **Behaviour `/api/*` → origin.<domain>:**
  - Redirect HTTP to HTTPS, all methods.
  - Cache policy **`CachingDisabled`**.
  - Origin request policy: a **custom** one with *All viewer headers* and the CloudFront header **`CloudFront-Viewer-Address`**, all cookies, all query strings. The rate limiter keys on this header; never trust `X-Forwarded-For`, because clients can set its first value.
- **Settings:**
  - alternate domain names `<domain>` and `www.<domain>`.
  - certificate from step 3.1.
  - price class *North America, Europe, Asia, Middle East and Africa* (includes India edge locations).
  - default root object `index.html`.

Route 53: `A` and `AAAA` alias records for `<domain>` and `www.<domain>` → the distribution.

### WAF (web ACL, scope CloudFront, us-east-1)
- **Managed rules:**
  - `AWSManagedRulesCommonRuleSet`, but set **`SizeRestrictions_BODY` to Count**. Otherwise it blocks bodies over 8 KB, which breaks avatar uploads (up to 5 MB).
  - `AWSManagedRulesKnownBadInputsRuleSet`.
  - `AWSManagedRulesAmazonIpReputationList`.
- **Rate-based rule:** 300 requests per 5 minutes per IP, action Block. This catches floods at the edge before they cost you compute.
- Associate the web ACL with the distribution.

## 12. Rate limiting, both layers
| Layer | What it stops | Config |
|---|---|---|
| WAF (edge) | floods, bots, known-bad IPs | web ACL above |
| App `RateLimitFilter` | credential stuffing, OTP/SMS abuse, guest-booking spam; answers `429 RATE_LIMITED` | `app.rate-limit.*` in `application.yml` |
| App domain throttles | per-email login lockout, per-phone OTP cooldown | `LoginAttemptLimiter`, `OtpService` |

The app buckets live in memory, which is correct for one task. Before running more than one task, move them to Postgres or Redis.

## 13. CI/CD (GitHub Actions)
`.github/workflows/ci.yml` runs the tests on every PR. `deploy.yml` deploys `main` once CI passes. One-time setup:
1. IAM → Identity providers → add **OpenID Connect**, `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`.
2. **Role `sahyatri-github-deploy`:**
   - Trust: that provider, with condition `token.actions.githubusercontent.com:sub` = `repo:<owner>/<repo>:environment:production`.
   - Permissions:
     - ECR push to `sahyatri-backend` (`ecr:GetAuthorizationToken` + the layer and put-image actions).
     - `ecs:DescribeTaskDefinition`, `ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`.
     - `iam:PassRole` on both task roles.
     - `s3:ListBucket` / `PutObject` / `DeleteObject` on the web bucket.
     - `cloudfront:CreateInvalidation`.
3. GitHub → Settings → Environments → create **production** (optionally with required reviewers).
4. Add these **repository variables:**

   | Variable | Value |
   |---|---|
   | `AWS_ROLE_ARN` | the deploy role's ARN |
   | `ECR_REPOSITORY` | `sahyatri-backend` |
   | `ECS_CLUSTER` | `sahyatri` |
   | `ECS_SERVICE` | the service name |
   | `ECS_TASK_FAMILY` | `sahyatri-backend` |
   | `ECS_CONTAINER` | `backend` |
   | `SPA_BUCKET` | the web bucket |
   | `CLOUDFRONT_DISTRIBUTION_ID` | the distribution's ID |
   | `VITE_GOOGLE_CLIENT_ID` | your OAuth client id |

## 14. Third parties
- **Razorpay** → Webhooks → `https://<domain>/api/webhooks/razorpay`, events `payment.captured`, `order.paid`, `payment.failed`, `refund.processed`, `refund.failed`, secret = `RAZORPAY_WEBHOOK_SECRET`. Do this once in **test** mode and again in **live** mode; each mode has its own webhooks and keys.
- **Google OAuth client:** add `https://<domain>` to *Authorised JavaScript origins*.

## 15. Go live
1. Deploy with Razorpay **test** keys. This stack is your staging until launch.
2. Run through it end to end:
   - sign up and get the verification email;
   - book, pay with a test card, get the confirmation email, and confirm the webhook shows up in the logs;
   - cancel and see the refund;
   - hammer `/api/auth/login` until it returns 429.
3. Fill in `frontend/src/lib/business.ts` and have the Terms and Privacy pages reviewed, then submit the site to Razorpay.
4. Swap in the live keys and the live webhook secret, then redeploy (ECS → Update service → Force new deployment).
5. Make a real ₹1 test departure, book it, then cancel and refund it.

## 16. Operating it
- **Logs:** CloudWatch → Log groups → `/ecs/sahyatri-backend`.
- **Alarms (CloudWatch → SNS email):**
  - ALB `HTTPCode_Target_5XX_Count` > 5 in 5 min;
  - `UnHealthyHostCount` ≥ 1;
  - RDS `CPUUtilization` > 80%;
  - RDS `FreeStorageSpace` < 2 GB;
  - SES bounce rate > 5%.
- **Uptime:** a free external check (UptimeRobot / Better Stack) on `https://<domain>/api/public/health`.
- **Rollback:** the circuit breaker rolls back failed deploys automatically. To roll back by hand, update the service to the previous task definition revision.
  - A migration can't be rolled back. Keep migrations additive, and never edit an applied one.
- **Database restore:** RDS → Restore to point in time. This creates a new instance; point `DB_URL` at it.
- **Scaling up:**
  1. First, raise the task size (1 vCPU / 2 GB).
  2. Before running more than one task:
     - add ShedLock, so `PaymentReconciler` and `DepartureLifecycleJob` run once;
     - move the rate-limit buckets to a shared store.
  3. Then raise the desired count or add target-tracking autoscaling on CPU.
  4. Move RDS to Multi-AZ when downtime starts costing money.
- **No Kubernetes.** EKS costs $73/mo for the control plane alone and adds ops work this app doesn't need.

## Monthly cost (approx., ap-south-1, 2026)
| Item | USD |
|---|---|
| Fargate 0.5 vCPU / 1 GB, 24×7 | ~18 |
| Application Load Balancer | ~18–20 |
| RDS db.t4g.micro + 20 GB gp3 + backups | ~15–18 |
| WAF (ACL + rules + requests) | ~8–10 |
| CloudFront, S3, Route 53, Secrets Manager, CloudWatch | ~5–8 |
| **Total** | **~65–75** (₹5.5–6.5k) |

SES costs about $0.10 per 1,000 emails, and MSG91 about ₹0.15–0.25 per OTP SMS.
