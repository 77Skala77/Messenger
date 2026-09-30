# Phone + Emulator Relay QA

> **HISTORICAL (2026-09-30):** this document describes the removed legacy prototype (static-ECDH session layer, `STANDARD_AEAD`, v1 invitations, reciprocal pairing, device approval states), deleted by DEC-021. It is kept for history and audit context only and does not describe current code. Current design: [Protocol v2](PROTOCOL_V2.md).

## Роль И Граница Доказательств

**Проверенная база репозитория:** `83785913910b9ee2ccc63f96c6c922cd39e678c5`

Этот документ описывает ручной debug QA между физическим Android-телефоном и Android Emulator.
Он поддерживает два разных сетевых маршрута:

1. один локальный Node relay на ноутбуке, доступный телефону по LAN IP и эмулятору по
   `10.0.2.2`;
2. один временный публичный HTTPS relay или reverse tunnel, доступный обоим клиентам по
   одинаковому URL.

Это процедура, а не отчёт о pass. Исторические результаты не подтверждают текущий commit,
устройства, tunnel, VPS или endpoint.

Приложение остаётся непроизводственным MVP:

- normal chats используют `SimplifiedSessionManager` и `STANDARD_AEAD`;
- Signal-backed sessions выключены; `PARANOIA_C4_AEAD` полностью удалён из кода (2026-09-27);
- warning-only первый контакт не подтверждает личность владельца ключа;
- WorkManager не гарантирует своевременную доставку;
- public HTTPS защищает сетевой hop, но не скрывает метаданные от relay/tunnel operator;
- независимый аудит и production relay отсутствуют.

Используйте одноразовые профили и бессодержательные тестовые сообщения.

## Выбор Маршрута

### Маршрут A — локальный relay на ноутбуке

Подходит, когда телефон и ноутбук находятся в одной доверенной Wi-Fi сети.

- физический телефон использует `http://<LAN_IP_ноутбука>:8787`;
- Android Emulator использует `http://10.0.2.2:8787`;
- URL различаются, но указывают на один Node relay process;
- cleartext HTTP допустим только в debug QA.

Этот маршрут не является публичным relay.

### Маршрут B — временный публичный HTTPS relay или tunnel

Подходит, когда телефон и эмулятор не могут обратиться к одному локальному endpoint.

- оба клиента используют один и тот же HTTPS base URL;
- endpoint должен быть временным, явно разрешённым владельцем и предназначенным только для
  одноразового QA;
- HTTP через интернет не допускается;
- tunnel provider, reverse proxy и relay operator считаются недоверенными;
- URL, токены, credentials и provider secrets нельзя коммитить или публиковать.

Не используйте исторический endpoint из старых QA-записей без новой авторизации и полной
проверки.

## Карточка Запуска

Заполните перед началом:

```text
Commit:
Debug APK:
Дата / время:
Ноутбук / ОС:
JDK / Gradle / Node.js:
Телефон / API / ABI:
Эмулятор / API / ABI:
Выбранный маршрут: A / B
Relay или tunnel provider:
Relay URL телефона:
Relay URL эмулятора:
Public URL expiry / owner, если применимо:
Tester:
```

В публичном отчёте маскируйте hostname, когда его раскрытие не требуется для воспроизведения.

## 1. Автоматизированные Предварительные Проверки

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

Запишите pass/fail/skip каждой команды. Наличие тестов в source не является pass.

## 2. Запуск Локального Node Relay

Для обоих маршрутов локальный relay может быть origin-сервисом:

```bash
cd android-app/server/relay
node src/server.mjs
```

Проверка на ноутбуке:

```bash
curl http://127.0.0.1:8787/health
```

Ожидаемый ответ:

```json
{"status":"ok"}
```

Текущий relay предоставляет:

```text
GET    /health
POST   /v1/messages
GET    /v1/messages/{queueId}
DELETE /v1/messages/{messageId}
```

Он хранит в памяти непрозрачные envelopes, queue ID, message ID и expiration timestamp. Это
не durable production queue и не high-availability service.

## 3. Настройка Маршрута A

### Физический телефон

Узнайте LAN IP ноутбука. На macOS обычно:

```bash
ipconfig getifaddr en0
```

При необходимости:

```bash
networksetup -listallhardwareports
```

Пример URL телефона:

```text
http://192.168.1.45:8787
```

### Android Emulator

Используйте host alias:

```text
http://10.0.2.2:8787
```

Правила:

- `10.0.2.2` не подходит физическому телефону;
- `127.0.0.1` и `localhost` на телефоне указывают на телефон;
- `0.0.0.0` — bind address сервера, а не client URL;
- firewall ноутбука должен разрешать LAN access к Node;
- не публикуйте port `8787` в интернет.

В приложении на обоих клиентах сохраните соответствующий URL и выполните health check.

## 4. Настройка Маршрута B

Выберите авторизованный временный HTTPS reverse tunnel или отдельный QA VPS. Следуйте
официальной документации выбранного provider и локальной security policy. Этот репозиторий не
делает конкретный tunnel tool обязательным.

Endpoint должен:

- завершать TLS с действительным сертификатом;
- проксировать только необходимый relay API;
- не изменять method, path или opaque body;
- поддерживать request body не меньше текущего relay limit;
- не кэшировать queue responses;
- не журналировать request body, ciphertext, полный queue ID или credentials;
- иметь ограниченный срок жизни и явного владельца;
- быть отключён после теста.

Проверьте публичный health endpoint:

```bash
curl https://temporary-relay.example/health
```

Ожидается тот же JSON health response. Затем укажите одинаковый HTTPS base URL на телефоне и
эмуляторе и повторите проверку в приложении.

Если `/health` доступен, это подтверждает только reachability. Это не проверка privacy,
retention, authentication, rate limits или production hardening.

## 5. Безопасность Публичного Endpoint

Перед тестом зафиксируйте:

- владельца инфраструктуры и разрешение на использование;
- срок жизни endpoint;
- где завершается TLS;
- какие access/error logs создают tunnel, proxy и hosting platform;
- кто имеет административный доступ;
- как endpoint будет отключён и удалён;
- что реальный пользовательский трафик не проходит через этот relay.

Не передавайте через публичный QA endpoint:

- реальные сообщения;
- реальные invitation payload;
- private/session keys;
- authentication tokens сторонних систем;
- персональные данные;
- production secrets.

TLS не скрывает от оператора IP, timing, queue identifiers, approximate envelope size,
frequency и факт использования одного relay двумя клиентами.

## 6. Установка И Одноразовые Профили

- Соберите и установите debug APK текущего commit на телефон и эмулятор.
- Создайте разные одноразовые локальные профили.
- Проверьте сохранение выбранного языка при перезапуске.
- Убедитесь, что экран настроек шифрования показывает только `Standard AEAD` — пункт `Paranoia C4` полностью удалён из интерфейса (2026-09-27).
- Не импортируйте старую базу или непроверенные ключи.

## 7. Взаимный Импорт Приглашений

1. Телефон A показывает invitation A.
2. Эмулятор B сканирует или вручную импортирует A.
3. Эмулятор B показывает invitation B.
4. Телефон A сканирует или вручную импортирует B.
5. На обоих клиентах подтвердите завершение двух импортов.
6. Проверьте `Готово к переписке`.

Invitation code можно копировать только между одноразовыми тестовыми клиентами. Не помещайте
его в issue, общий чат, Logcat или commit.

## 8. Warning-Only Первый Контакт

До независимой verification:

- contact должен быть `VerificationStatus.UNVERIFIED`;
- primary device — `DeviceApprovalStatus.PRIMARY_UNVERIFIED`;
- UI должен показывать постоянное предупреждение;
- тестовая отправка на primary device должна быть разрешена;
- состояние не должно называться `VERIFIED`.

`Готово к переписке` означает пригодный маршрут, а не доказанную личность.

## 9. Preflight

На обоих клиентах:

1. откройте проверяемую переписку;
2. откройте `Готовность к тесту переписки`;
3. проверьте relay mode, URL, health, pairing, primary-device state и retry counts;
4. запустите проверку.

Для маршрута B UI должен распознавать HTTPS public/tunnel mode. Preflight не является
криптографической аттестацией endpoint или контакта.

## 10. Полный Цикл A → B И B → A

Для каждого направления:

1. Отправьте уникальное бессодержательное сообщение.
2. На отправителе различите `PENDING`/`FAILED` и `SENT`.
3. На получателе выполните manual sync, если fetch задержан.
4. Проверьте один экземпляр `RECEIVED`.
5. Проверьте постановку encrypted delivery receipt.
6. На отправителе обработайте receipt.
7. Проверьте переход исходного сообщения в `DELIVERED`.

`SENT` означает relay acceptance. `DELIVERED` означает authenticated receipt о
receiver-app acceptance, decryption и durable storage. Это не read receipt.

## 11. Независимая Verification

После warning-only проверки:

1. сравните safety number или verification QR по доверенному или независимому каналу;
2. при полном совпадении отметьте contact и primary device verified;
3. проверьте исчезновение warning только после verification;
4. повторите сообщение в обе стороны с receipt round trip.

Несовпадение нельзя обходить ручным подтверждением.

## 12. Retry И Восстановление

### Маршрут A

Остановите локальный Node relay.

### Маршрут B

Остановите origin relay или временный tunnel только если это разрешено и не затрагивает
других пользователей. Не тестируйте отказ на shared infrastructure без согласования.

Затем:

1. отправьте новое уникальное сообщение;
2. зафиксируйте `PENDING`/`FAILED`, attempt count и next retry;
3. восстановите endpoint;
4. снова проверьте `/health`;
5. дождитесь best-effort WorkManager либо выполните manual sync для разделения scheduler и
   delivery pipeline;
6. выполните fetch на получателе;
7. проверьте один plaintext instance;
8. проверьте receipt и `DELIVERED`.

Не записывайте конкретное число секунд как гарантию WorkManager.

## 13. Duplicate, Restart И Invalid Blob

- перезапуск клиентов не должен терять accepted message, replay marker или delivery status;
- повторная синхронизация не должна создавать duplicate plaintext;
- receipt не должен отображаться как chat message;
- повторный authenticated envelope должен подавляться;
- malformed или unauthenticated envelope не должен отображаться и может остаться на relay
  до TTL или внешнего удаления;
- ручное изменение ciphertext не требуется для обычного QA.

## 14. Диагностика И Logcat

Сохраняйте только sanitized evidence:

- commit, device/API/ABI;
- relay mode и health timestamps;
- статусы, counts и next retry;
- короткие queue fingerprints;
- event codes и last error;
- provider status без credentials.

Не сохраняйте message text, полный queue ID, invitation payload, keys, nonce, ciphertext,
provider token или server secret.

Для Android logs используйте [LOGCAT_QA.md](LOGCAT_QA.md).

## 15. Завершение И Cleanup

После теста:

1. остановите local relay, если он больше не нужен;
2. остановите и удалите временный tunnel;
3. отключите временный VPS endpoint согласно разрешённой процедуре;
4. удалите локальные временные config/log files с credentials;
5. выполните debug reset одноразовых app profiles при необходимости;
6. помните, что reset сохраняет Relay URL и не является forensic erase;
7. проверьте, что публичный `/health` больше недоступен, если endpoint должен быть удалён;
8. сохраните только sanitized report.

Client reset не гарантирует удаление каждого blob с внешнего relay. Учитывайте delete, TTL и
поведение инфраструктуры.

## Исторические Результаты

Репозиторий содержит исторические записи о:

- phone + emulator через один локальный Node relay;
- двусторонней доставке;
- retry recovery, receipts и duplicate suppression;
- временном Ubuntu VPS;
- HTTP-by-IP endpoint `194.87.226.46`;
- systemd, Caddy, UFW и reboot recovery.

Это исторический QA-контекст. Он не подтверждает текущий commit, доступность или владение VPS,
endpoint reachability, DNS, TLS, firewall, SSH, logs, retention, abuse controls или
production readiness. Исторический HTTP endpoint нельзя использовать как действующий адрес.

## Критерии Успеха

Отчёт должен содержать:

- точный commit и среду;
- pass/fail/skip предварительных команд;
- выбранный маршрут и явное различение local/public;
- health обоих клиентов;
- mutual pairing;
- warning-only first-contact state;
- independent verification transition;
- A → B и B → A с `SENT`, `RECEIVED`, `DELIVERED`;
- receipt behavior;
- retry recovery без duplicate plaintext;
- cleanup public endpoint;
- все failures и skips.

## Связанные Документы

- [Manual Two-Device QA](MANUAL_TWO_DEVICE_QA.md)
- [Two Physical Devices](TWO_DEVICE_TEST.md)
- [Relay Public Testing](RELAY_PUBLIC_TESTING.md)
- [VPS Relay Deployment](VPS_RELAY_DEPLOYMENT.md)
- [Logcat QA](LOGCAT_QA.md)
- [MVP Delivery Baseline](MVP_DELIVERY_BASELINE.md)
- [Known Limitations](KNOWN_LIMITATIONS.md)
