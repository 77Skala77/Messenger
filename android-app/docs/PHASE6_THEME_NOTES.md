# Phase 6 — Color notes from Telegram's bundled themes

Status: **NOTES** (2026-10-01, Phase 6 study). Source: five theme files shipped in Telegram for
Android (`TMessagesProj/src/main/assets/{day,night,arctic,darkblue,bluebubbles}.attheme`, commit
`f2908b1`), copied read-only to `D:\Tools\reference\themes`. The files carry no license header
(rule 2 in `PHASE6_UI_PLAN.md`; the owner's accepted legal risk, DEC-027). We use **only the colors,
as plain numbers**, in our own theme files; nothing else is copied.

Format: `name=value` lines, the value a signed 32-bit ARGB integer (`-1` = white, `-14509328` =
`#FF2187F0`). Below the values are converted to `#RRGGBB`; `/aNN` is an alpha below 255 (0–255).
The files hold 186 (`bluebubbles`) to 498 (`night`) keys each (every control of the app).

## 1. What the themes tell us

- **Day** is white and light gray surfaces, dark gray text `#333333`, one blue accent family
  (`#229AF0` for send and counters). Bubbles: incoming light gray, outgoing saturated blue with white
  text, a slight vertical gradient on outgoing bubbles in some themes.
- **Night** (neutral dark gray, near black wallpaper) and **darkblue** (blue-gray) are the two dark
  variants; `arctic` is a light blue-sky variant and `bluebubbles` a reduced set.
- The look is one accent color, soft shadows (alpha near 0 to 50), and **high contrast text**
  (white on dark, `#333333` on light).

## 2. Colors worth taking (light = `day`, dark = `night`)

| Role | Light (day) | Dark (night) | Telegram key |
| --- | --- | --- | --- |
| Top bar | `#FFFFFF` | `#232326` | `actionBarDefault` |
| Top bar title | `#333333` | `#FFFFFF` | `actionBarDefaultTitle` |
| Screen background (list) | `#FFFFFF` | `#181819` | `windowBackgroundWhite` |
| Section / gap background | light gray | `#000000` | `windowBackgroundGray` |
| Primary text | `#333333` | `#FFFFFF` | `windowBackgroundWhiteBlackText` |
| Secondary text | `#808384` | `#FFFFFF` at alpha 110 | `windowBackgroundWhiteGrayText` |
| Link / accent text | `#238AE3` | `#64B5EF` | `windowBackgroundWhiteBlueText` |
| Chat-list name | (primary) | `#F1F1F1` | `chats_name` |
| Chat-list last message | (secondary) | `#828282` | `chats_message` |
| Chat-list time | (secondary) | `#787878` | `chats_date` |
| Unread badge | `#24AEF7` | `#399EF3` | `chats_unreadCounter` |
| Unread badge, muted chat | gray | `#454545` | `chats_unreadCounterMuted` |
| Tick of a sent message in the list | `#1A9FEF` | `#46ADF3` | `chats_sentCheck` |
| Round action button | `#51A2E8` | `#5EA0DB` | `chats_actionBackground` |
| Divider | `#E6E6E6` | `#000000` | `divider` |
| Chat wallpaper | `#FFFFFF` | `#0F0F10` | `chat_wallpaper` |
| Incoming bubble | `#F0F0F0` | `#1F2123` | `chat_inBubble` |
| Outgoing bubble | `#2D7ED5` | `#366CAF` | `chat_outBubble` |
| Outgoing bubble, gradient end | `#5CB6E0` | `#3B8CB9` | `chat_outBubbleGradient` |
| Text in incoming bubble | dark | `#FAFAFA` | `chat_messageTextIn` |
| Text in outgoing bubble | `#FFFFFF` | `#FAFAFA` | `chat_messageTextOut` |
| Time in incoming bubble | `#939599` | `#7D7F81` | `chat_inTimeText` |
| Time in outgoing bubble | `#C7E6FF` | `#94D3F6` | `chat_outTimeText` |
| Tick in outgoing bubble | white, alpha 245 | `#C9F0FF` alpha 225 | `chat_outSentCheck` |
| Clock (pending) in outgoing bubble | white, alpha 112 | `#99C9EF` | `chat_outSentClock` |
| Date chip / service message | `#FFFFFF` alpha 244 | `#373737` alpha 150 | `chat_serviceBackground` |
| Input bar background | (white) | `#1E1E1F` | `chat_messagePanelBackground` |
| Input text / hint | dark / gray | `#FFFFFF` / white alpha 100 | `chat_messagePanelText/Hint` |
| Send button | `#229AF0` | `#229AF0` | `chat_messagePanelSend` |
| Reply line, incoming | `#3E91E0` | `#79C4FC` | `chat_inReplyLine` |
| Name of the sender in a bubble | blue `#2586D6`, pink `#CB4F86`, violet `#836FE6` | blue `#5CADEA`, cyan `#53CCC6`, green `#7DD071`, orange `#E8AB5A`, pink `#E8749A`, red `#E88878`, violet `#8C8DEA` | `avatar_nameInMessage*` |
| Avatar backgrounds | blue `#50A5E6` | red `#DC805B`, orange `#F2BC64`, blue `#4FA1E8` | `avatar_background*` |

Gaps (`-` in the extraction): the day theme leaves some keys unset because they inherit another
key's value in Telegram's theme engine. Our theme sets every color itself.

## 3. How this maps to our app

- Our two themes follow Material 3 color roles; the table gives starting values, not a copy of
  Telegram's engine (which uses ~500 keys). A first token set: `surface`, `surfaceVariant`,
  `onSurface`, `onSurfaceVariant`, `primary` (the blue), `bubbleIncoming`, `bubbleOutgoing`,
  `onBubbleOutgoing`, `tick`, `badge`, `chip`, `divider`, `wallpaper`.
- Ticks follow our semantics, not Telegram's: one tick = "sent to the delivery server", two = "delivered"
  (an authenticated receipt); **never a "read" state** (the protocol has none). Telegram's
  `chats_sentReadCheck` keys are therefore unused.
- Avatar colors: our rule is a stable pick from a small palette by a hash of the local contact ID
  (never sent anywhere), using the seven hues above.
- Contrast: every pair (text on bubble, time on bubble, badge text on badge) is checked in the
  theme test against WCAG AA before it ships (a planned unit test).
- Dynamic color (Android 12+ wallpaper colors) stays off for chat bubbles so the look is stable;
  an option for the rest of the app is a later decision.

## 4. Next

- Step 6c writes `Theme.kt` with these tokens for both modes and a preview screen.
- The owner's screenshots of Telegram decide details the files cannot show (spacing, tail shape).
