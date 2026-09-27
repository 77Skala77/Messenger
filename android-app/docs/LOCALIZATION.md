# Localization

## Role And Evidence Boundary

**Repository baseline reviewed:** `dc684a00acd37c6e7f4108b8e535483745a7143f`

This document defines localization policy, terminology, and manual verification scope for the
current Russian and English UI. It is not proof that every visible string was inspected or
that localization tests passed for the reviewed commit.

Current resources are maintained in:

- `core/ui/src/main/res/values/strings.xml` — English;
- `core/ui/src/main/res/values-ru/strings.xml` — Russian.

The application is Russian-first for a user who has not made an explicit in-app language
choice. Settings permits a manual Russian or English selection through the AppCompat per-app
locale host. The selected language is intended to apply immediately and survive restart.

A fresh executable verification run must confirm these behaviors for the selected commit.

## Security Claim Boundary

Localized wording must preserve the same security meaning in both languages.

The application is a development and audit-oriented MVP prototype. It is not externally
audited, production-ready, or suitable for real sensitive communications. Legacy UI wording
such as `AUDIT-READY PROTOTYPE` / `ПРОТОТИП ДЛЯ АУДИТА` means that material exists for review;
it must not be interpreted as evidence that an independent audit was completed.

Translations must not imply that:

- the prototype session is Signal, PQXDH, or Double Ratchet;
- warning-only first contact independently verifies the human owner of the key;
- `DELIVERED` means that a person read the message;
- WorkManager guarantees prompt delivery;
- local reset provides forensic secure erasure;
- a public relay, tunnel, or VPS is production infrastructure;
- disabled Signal boundaries are active security features. (`PARANOIA_C4_AEAD` no longer exists in the codebase — removed 2026-09-27.)

## Locale Policy

- Russian is the default in-app language before an explicit user selection.
- English is the secondary supported language.
- The user's explicit application-language choice takes precedence over the system locale.
- The system locale must not silently replace the intended Russian first-launch default.
- Language switching must not change profile, contact, trust, message, retry, or relay state.
- A missing Russian translation must be treated as localization debt rather than accepted
  mixed-language UX.
- All user-facing Compose text, accessibility descriptions, dialogs, warnings, errors, and
  status labels should resolve from Android resources. Dynamically formatted values must use
  localized format resources rather than English sentence assembly in Kotlin.
- Technical identifiers, enum names, protocol version labels, URLs, and code symbols remain
  untranslated when exact spelling is required for debugging or compatibility.

## Canonical Security Terminology

Technical documentation and diagnostics must distinguish persisted identifiers from localized
labels.

### Contact identity verification

| Runtime identifier | English UI meaning | Russian UI meaning |
| --- | --- | --- |
| `VerificationStatus.VERIFIED` | Verified | Проверено |
| `VerificationStatus.UNVERIFIED` | Unverified | Не проверено |
| `VerificationStatus.KEY_CHANGED` | Key Changed | Ключ изменён |
| `VerificationStatus.UNKNOWN` | Unknown | Неизвестно |

`UNVERIFIED` is the warning-only first-contact state. It permits current onboarding only when
the matching primary device is `PRIMARY_UNVERIFIED`; it does not establish independent human
identity authenticity.

### Per-device delivery approval

| Runtime identifier | English UI meaning | Russian UI meaning |
| --- | --- | --- |
| `DeviceApprovalStatus.PRIMARY_UNVERIFIED` | Primary device, identity not independently verified | Основное устройство, личность независимо не проверена |
| `DeviceApprovalStatus.VERIFIED` | Verified device | Проверенное устройство |
| `DeviceApprovalStatus.PENDING_APPROVAL` | Pending approval | Ожидает проверки |
| `DeviceApprovalStatus.KEY_CHANGED` | Device key changed | Ключ устройства изменён |
| `DeviceApprovalStatus.REVOKED` | Revoked | Отозвано |

Localized text must not collapse contact verification and device approval into one generic
"device state" concept.

### Pairing and readiness

| English | Russian | Meaning boundary |
| --- | --- | --- |
| Waiting for return invitation | Ожидает обратное приглашение | Reciprocal receive address is still missing. |
| Ready for conversation | Готово к переписке | Current routing/pairing prerequisites are usable; this is not an identity-verification claim. |
| Device blocked | Устройство заблокировано | Delivery policy currently rejects the target. |

### Delivery status

| Runtime status | English | Russian | Meaning |
| --- | --- | --- | --- |
| `PENDING` | Waiting for sync | Ожидает синхронизации | Ciphertext is stored locally and awaits an attempt. |
| `FAILED` | Waiting for sync / Send failed | Ожидает синхронизации / Ошибка отправки | A relay attempt failed; retry metadata may remain. |
| `SENT` | Sent to delivery server | Отправлено на сервер доставки | The relay accepted the blob. |
| `DELIVERED` | Delivered | Доставлено | An authenticated receipt confirmed receiver-app acceptance, decryption, and durable storage. |
| `RECEIVED` | Received | Получено | The local receiver app accepted and durably stored the message. |

`DELIVERED` / `Доставлено` is not a human read receipt. User read receipts are not implemented.

## General Product Terminology

| English | Russian |
| --- | --- |
| Secure Messenger | Защищённый мессенджер |
| Encryption Suite | Режим шифрования |
| Standard AEAD | Стандартное шифрование |
| Contact | Контакт |
| Invitation | Приглашение |
| QR Verification | Проверка через QR-код |
| Safety Number | Контрольный код безопасности |
| Relay Server | Сервер доставки |
| Relay server available / unavailable | Сервер доступен / Сервер недоступен |
| Synchronize now | Синхронизировать сейчас |
| Delete Local Data | Удалить локальные данные |
| Reset Test Data | Сбросить тестовые данные |
| Conversation Test Readiness | Готовность к тесту переписки |
| Public relay or tunnel | Публичный сервер доставки или туннель |
| Copy invitation code | Скопировать код приглашения |
| Delivery Diagnostics | Диагностика доставки |

Avoid unnecessary untranslated English nouns such as `relay`, `tunnel`, `debug`, or `release`
in ordinary Russian user-facing sentences when a clear Russian equivalent is available.
Exact build-variant names, URLs, paths, and code identifiers may remain English where needed.

## Security Warning Requirements

Both locales must communicate these points clearly where relevant:

- first-contact encryption does not prove who owns the imported key;
- safety-number or verification-QR comparison requires a trusted or independent channel;
- a changed key may indicate reinstall, device replacement, or attack;
- pending, changed, revoked, or missing primary-device state blocks current delivery;
- background sync is best effort;
- debug HTTP exposes transport and metadata risk;
- public HTTPS protects the network hop but not metadata from the relay operator;
- reset deletes documented local QA state but retains the selected relay URL and is not
  forensic erasure;
- Signal-backed sessions remain disabled. `PARANOIA_C4_AEAD` no longer exists in the codebase (removed 2026-09-27), so there is no longer a corresponding UI string to translate.

Warnings should be direct and must not be weakened for brevity.

## Resource Maintenance Checklist

For every new or changed user-facing resource:

- add the same resource name to English and Russian files;
- preserve matching format-argument count, order, and type;
- verify apostrophe, quote, percent, newline, and XML escaping;
- avoid concatenating translated fragments into a sentence;
- use plural resources when quantity affects grammar;
- provide localized content descriptions for actionable and security-relevant controls;
- keep security notices, error messages, diagnostics, and feature-disabled explanations in
  sync with the code change;
- do not expose secrets, full queue identifiers, keys, nonces, ciphertext, or message text
  through translated diagnostics;
- review narrow-width wrapping, large font scale, and long user-provided names or URLs;
- check that buttons and dialogs do not truncate the security-critical action or warning;
- review Russian wording with a fluent speaker before release-oriented use.

## Manual Verification Matrix

Record exact commit, device or emulator, API level, system locale, font scale, and pass/fail
result.

Verify at minimum:

1. Fresh app state on an English-configured emulator opens in Russian before manual choice.
2. Manual switch to English applies across currently visible and newly opened screens.
3. Process restart preserves the explicit English choice.
4. Switching back to Russian applies immediately and survives restart.
5. The complete language-option row is clickable, including label and radio indicator.
6. Onboarding, empty states, invitations, scanner prompts, contact security, conversation,
   Settings, relay configuration, QA preflight, diagnostics, reset, and errors use the
   selected language.
7. Warning-only unverified identity, key-change, pending approval, revoked device, and
   delivery-blocked wording retain their distinct meanings.
8. `SENT`, `DELIVERED`, retry, and received labels remain semantically correct.
9. Public relay, debug HTTP, release HTTPS, loopback, and `0.0.0.0` warnings are understandable
   without treating a test relay as production infrastructure.
10. Narrow screens and large accessibility font sizes do not hide or materially truncate
    warnings, confirmation actions, or status meaning.
11. QR invitation and verification scanner content descriptions are localized; the scanner
    activity remains portrait-only while the rest of the app is not described as globally
    orientation-locked.
12. Language switching does not alter stored security or delivery state.

## Automated Evidence Boundary

Repository test source may cover language defaulting, persistence, and UI interaction. The
existence of such tests is not a pass result. A current verification record must state the
exact command and whether each relevant test passed, failed, or was skipped.

The standard instrumented command is documented in the project README and
`MVP_DELIVERY_BASELINE.md`. Do not claim localization completion solely because both resource
files contain the same number of entries.

## Related Documents

- [Project README](../README.md)
- [Device Trust Policy](ai-workflow/DEVICE_TRUST_POLICY.md)
- [MVP Delivery Baseline](MVP_DELIVERY_BASELINE.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
- [Manual Two-Device QA](MANUAL_TWO_DEVICE_QA.md)
- [Logcat QA](LOGCAT_QA.md)
