# Ручной QA Двух Устройств

## Роль Документа

**Проверенная база репозитория:** `9a9de1d33056d8824335c60d5e1d04f77c02e4d3`

Это компактный checklist тестового дня для текущего debug build. Подробная настройка локального
relay и двух физических телефонов находится в [TWO_DEVICE_TEST.md](TWO_DEVICE_TEST.md).

Этот файл не является отчётом о выполненном тесте. Наличие шага или старого результата не
доказывает pass для текущего commit.

Сценарий проверяет разработческий MVP, а не production security:

- normal chats используют `SimplifiedSessionManager` и `STANDARD_AEAD`;
- Signal-backed sessions и `PARANOIA_C4_AEAD` выключены;
- warning-only первый контакт допускает переписку без независимой проверки личности;
- LAN HTTP предназначен только для debug QA;
- WorkManager не гарантирует своевременную доставку;
- reset не является forensic secure erase.

## Выбор Сценария

Используйте только один основной маршрут за тестовый запуск:

- **два физических телефона в одной Wi-Fi сети** — этот checklist и
  `TWO_DEVICE_TEST.md`;
- **физический телефон + Android Emulator через доступный обоим public relay** —
  `PHONE_EMULATOR_PUBLIC_RELAY_QA.md`;
- **исследование вариантов публичной доступности relay** — `RELAY_PUBLIC_TESTING.md`.

Не называйте phone + emulator через локальный ноутбук «public relay» и не используйте
исторический VPS без новой авторизации и проверки.

## Карточка Тестового Запуска

Заполните до начала:

```text
Commit:
Debug APK:
Дата / время:
Ноутбук / ОС:
JDK / Gradle / Node.js:
Устройство A / API / ABI:
Устройство B / API / ABI:
Relay mode:
Relay URL A:
Relay URL B:
Tester:
```

Используйте одноразовые профили и бессодержательные тестовые сообщения.

## 1. Предварительные Автоматизированные Проверки

Из корня репозитория:

```bash
cd android-app
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:signal-protocol:testDebugUnitTest :core:signalstore:test test
```

Relay:

```bash
cd android-app/server/relay
node --test
```

Запишите pass/fail/skip для каждой команды. Ошибка не превращается в pass после продолжения
ручного теста.

## 2. Relay И Сеть

Для LAN-сценария:

```bash
cd android-app/server/relay
node src/server.mjs
curl http://127.0.0.1:8787/health
```

Ожидается:

```json
{"status":"ok"}
```

На физическом телефоне:

- `127.0.0.1` и `localhost` означают сам телефон;
- `0.0.0.0` не является URL клиента;
- `10.0.2.2` предназначен для Android Emulator;
- используйте LAN IP ноутбука для двух физических телефонов.

Зафиксируйте firewall, VPN, captive portal и смену Wi-Fi как возможные причины ошибки.

## 3. Установка И Чистое Состояние

- Установите одну debug APK текущего commit на оба устройства.
- Создайте два отдельных одноразовых профиля.
- Не используйте настоящие имена, сообщения или invitation payload в отчёте.
- Проверьте, что `Paranoia C4` недоступна.
- Не восстанавливайте старую базу, ключи или тестовые данные из непроверенного backup.

## 4. Быстрая Проверка Локализации

На каждом устройстве:

1. Переключите интерфейс на English нажатием по всей строке языка.
2. Вернитесь на Русский тем же способом.
3. Перезапустите приложение и проверьте сохранение выбранного языка.
4. Убедитесь, что security warnings, status labels, dialogs и scanner descriptions меняются
   вместе с языком.

Полная матрица находится в [LOCALIZATION.md](LOCALIZATION.md). Локализация не должна менять
trust, pairing, message или retry state.

## 5. Настройка Relay И Диагностики

На каждом устройстве:

1. Откройте `Настройки` → `Сервер доставки`.
2. Сохраните корректный URL для выбранного сценария.
3. Проверьте сервер.
4. Зафиксируйте результат health check.
5. Убедитесь, что debug HTTP показывает предупреждение.
6. Откройте `Диагностика доставки`.
7. Проверьте, что отчёт содержит статусы, counts, timestamps и короткие fingerprints, но не
   message text, keys, nonce, ciphertext или полный queue ID.

## 6. Взаимный Pairing

1. A показывает invitation A; B импортирует его.
2. B показывает invitation B; A импортирует его.
3. При проблеме камеры используйте копирование и вставку invitation code только между
   одноразовыми тестовыми устройствами.
4. Проверьте portrait-only режим invitation scanner.
5. Проверьте portrait-only режим verification scanner.
6. Подтвердите оба импорта в UI.
7. Убедитесь, что pairing показывает `Готово к переписке`.

`Готово к переписке` означает готовность текущего двустороннего маршрута, а не независимую
verification личности.

## 7. Warning-Only Первый Контакт

До сравнения safety number:

- contact должен быть `VerificationStatus.UNVERIFIED`;
- matching primary device — `DeviceApprovalStatus.PRIMARY_UNVERIFIED`;
- UI должен постоянно предупреждать, что личность не проверена;
- отправка бессодержательного тестового сообщения на primary device должна быть разрешена;
- state не должен называться `VERIFIED`.

Отсутствие предупреждения или блокировка нормального warning-only onboarding — отдельный
дефект.

## 8. Preflight

После выбора переписки:

1. Откройте `Настройки` → `Готовность к тесту переписки`.
2. Проверьте relay URL и последний health result.
3. Проверьте pairing status и primary-device state.
4. Проверьте pending/failed counts и worker status.
5. Запустите preflight.

Ожидаемый положительный результат: `Готово к тесту переписки`.

Preflight должен объяснять остановку при missing URL, недоступном relay, loopback или
`0.0.0.0` на телефоне, incomplete pairing, blocked device state или retry backlog. Preflight
— вспомогательная QA-проверка, а не криптографическая аттестация.

## 9. A → B И B → A

Для каждого направления:

1. Отправьте уникальное короткое тестовое сообщение.
2. На отправителе различите:
   - `Ожидает синхронизации` (`PENDING`/`FAILED`);
   - `Отправлено на сервер доставки` (`SENT`).
3. На получателе выполните manual sync, если автоматический fetch задержан.
4. Проверьте ровно один видимый экземпляр со статусом `Получено` (`RECEIVED`).
5. Получатель должен поставить encrypted delivery receipt в обратную очередь.
6. На отправителе обработайте receipt и проверьте `Доставлено` (`DELIVERED`).

`DELIVERED` подтверждает receiver-app acceptance, decryption и durable storage. Это не read
receipt и не доказательство, что человек увидел сообщение.

Запишите timestamps основных переходов и наличие/отсутствие соответствующих diagnostic events.

## 10. Независимая Verification

После проверки warning-only состояния:

1. Сравните safety number или verification QR по доверенному или независимому каналу.
2. При полном совпадении явно отметьте contact и primary device как verified.
3. Проверьте исчезновение unverified warning только после этого.
4. Повторите A → B и B → A с полным receipt round trip.

Несовпадение verification QR нельзя обходить ручным подтверждением.

## 11. Retry После Остановки Relay

1. Остановите relay.
2. Отправьте новое уникальное сообщение.
3. Проверьте сохранение `PENDING`/`FAILED`, attempt count и next retry.
4. Зафиксируйте retry-scheduled diagnostic event, если он доступен.
5. Запустите relay и повторно проверьте `/health`.
6. Дождитесь best-effort WorkManager в разумном тестовом окне.
7. Если нужно разделить проблему scheduler и pipeline, нажмите manual sync.
8. На получателе выполните fetch при необходимости.
9. Проверьте ровно один plaintext instance.
10. Проверьте возврат receipt и переход в `DELIVERED`.

Не фиксируйте конкретную задержку WorkManager как гарантированное значение. Android может
изменять scheduling из-за power management, сети и состояния процесса.

## 12. Restart И Duplicate

- Перезапустите приложения после успешной доставки.
- Проверьте сохранение сообщения, delivery status, session counter и replay behavior.
- Повторная синхронизация не должна создавать duplicate plaintext.
- Receipt control message не должна отображаться в чате.
- Ручное изменение ciphertext не входит в обычный пользовательский checklist.
- Если harness повторно подаёт authenticated envelope, ожидается duplicate suppression.
- Malformed или unauthenticated blob не должен отображаться как сообщение и может остаться
  на relay до TTL или внешнего удаления.

## 13. Device-State Matrix

Текущий planner выбирает только primary device и не выполняет multi-device fan-out.

Проверяйте только специально подготовленными одноразовыми данными:

- `PRIMARY_UNVERIFIED` — разрешён primary target с warning;
- `VERIFIED` — разрешён primary target;
- `PENDING_APPROVAL` — blocked;
- device `KEY_CHANGED` — blocked;
- `REVOKED` — blocked;
- missing primary — blocked;
- contact `VerificationStatus.KEY_CHANGED` — interactive send blocked.

Полное fail-closed поведение для `VerificationStatus.UNKNOWN`, invalid persisted enum,
migration 1 → 2, restart и queued retry ещё не считается доказанным. Не помечайте этот пункт
pass без отдельной targeted regression matrix.

## 14. Debug Reset

`Сбросить тестовые данные` предназначен для одноразового QA-состояния. После подтверждения
текущий runtime удаляет документированные local profile, contacts, messages, sessions,
replay/retry, disabled Signal-spike records и diagnostics, но сохраняет Relay URL.

Reset:

- не является forensic erase;
- не гарантирует стирание historical flash blocks;
- не удаляет автоматически каждый blob на внешнем relay;
- не доказывает отсутствие plaintext в памяти, клавиатуре, clipboard, notification или
  screenshot surface.

## 15. Logcat И Incident Capture

На одном выбранном устройстве из `android-app`:

```bash
./scripts/start_qa_logcat.sh
# воспроизведите проблему и нажмите Ctrl-C
./scripts/analyze_qa_logcat.sh qa-logcat.txt
```

Следуйте [LOGCAT_QA.md](LOGCAT_QA.md). Не публикуйте raw log до ручной проверки и redaction.

## 16. Если Тест Не Прошёл

Запишите:

- commit и APK;
- device model, API, ABI и battery/network state;
- направление A → B или B → A;
- relay mode, URL и health timestamp;
- preflight result;
- visible message statuses;
- pending/failed count и next retry;
- sanitized diagnostic report;
- sanitized Logcat summary;
- relay process output без envelopes и identifiers;
- точный шаг и наблюдаемое поведение;
- pass/fail/skip каждого предыдущего шага.

Не прикладывайте:

- message text;
- полный invitation payload;
- private/session keys;
- nonce;
- ciphertext;
- полный queue ID;
- live token, SSH private key или server secret;
- реальные персональные данные.

## Исторические Результаты

Репозиторий содержит исторические записи о phone + emulator и VPS QA, включая delivery,
retry recovery, receipts, duplicate suppression и reboot recovery. В старых записях также
упоминается дата 2026-05-29 и временный HTTP-by-IP endpoint.

Эти сведения:

- полезны как контекст ожидаемого поведения;
- не являются pass для текущего commit;
- не подтверждают текущую доступность или владение VPS;
- не подтверждают DNS, TLS, Caddy, systemd, UFW, SSH, logs или retention;
- не должны использоваться как production evidence.

## Критерии Завершения

Checklist завершён только когда существует запись с:

- точным commit и средой;
- результатами automated prerequisites;
- выбранным network scenario;
- mutual pairing;
- warning-only first-contact test;
- independent verification transition;
- A → B и B → A с `SENT`, `RECEIVED`, `DELIVERED`;
- retry recovery и duplicate check;
- reset/logcat результатами, если они были в scope;
- всеми failures и skips.

## Связанные Документы

- [Two Physical Devices](TWO_DEVICE_TEST.md)
- [Phone + Emulator Public Relay QA](PHONE_EMULATOR_PUBLIC_RELAY_QA.md)
- [Relay Public Testing](RELAY_PUBLIC_TESTING.md)
- [Localization](LOCALIZATION.md)
- [Logcat QA](LOGCAT_QA.md)
- [MVP Delivery Baseline](MVP_DELIVERY_BASELINE.md)
- [Device Trust Policy](ai-workflow/DEVICE_TRUST_POLICY.md)
