# Localization

## Policy

- Russian (`values-ru/strings.xml`) is the default first-launch language.
- English (`values/strings.xml`) is the secondary supported language.
- Settings allows an explicit user selection; it is saved locally, applied immediately
  through the AppCompat per-app locale host, and restored after restart.
- The system locale is not allowed to override the Russian default before the user chooses.
- All visible Compose text and security notices must resolve from Android string resources.

## Terminology

| English | Russian |
| --- | --- |
| Secure Messenger | Защищённый мессенджер |
| Encryption Suite | Режим шифрования |
| Standard AEAD | Стандартное шифрование |
| Paranoia C4 AEAD | Paranoia C4 |
| Contact | Контакт |
| Invitation | Приглашение |
| QR Verification | Проверка через QR-код |
| Safety Number | Контрольный код безопасности |
| Verified / Unverified | Проверено / Не проверено |
| Key Changed | Ключ изменён |
| Relay Server | Сервер доставки |
| Relay server available / unavailable | Сервер доступен / Сервер недоступен |
| Synchronize now | Синхронизировать сейчас |
| Waiting for return invitation | Ожидает обратное приглашение |
| Ready for conversation | Готово к переписке |
| Device blocked | Устройство заблокировано |
| Delete Local Data | Удалить локальные данные |
| Reset Test Data | Сбросить тестовые данные |
| Conversation Test Readiness | Готовность к тесту переписки |
| Public relay / tunnel | Публичный сервер / tunnel |
| Copy invitation code | Скопировать код приглашения |

## Localization Checklist

- Add every new user-facing key to both `values/strings.xml` and `values-ru/strings.xml`.
- Do not place visible strings directly in Kotlin or Compose code.
- Review Russian warnings with a native speaker for clarity and seriousness.
- Verify onboarding, empty states, errors, buttons, content descriptions and dialogs.
- Verify security-status and message-status wording in both languages.
- Verify relay URL, health status, synchronization and two-way pairing wording.
- Verify physical-phone loopback/`0.0.0.0` warnings, preflight results and debug reset confirmation in both languages.
- Verify public HTTPS relay/tunnel wording and manual invitation copy/paste wording.
- Test first launch defaults to Russian on an English-configured emulator.
- Test manual switch to English, process restart, and return to Russian.
- Test that the whole language-option row is clickable, including its text and radio-circle area.
- Check text wrapping on narrow screens and larger accessibility font sizes.
- Keep diagnostics and experimental-mode warnings translated together with feature work.
