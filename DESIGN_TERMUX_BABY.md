# Termux Baby (`termux-baby`) — UX/UI Design & Implementation Plan

> **Target Repository:** `termux-baby` (Fork of `termux/termux-app`, commit `8629e63`)  
> **UI Stack:** Java 8 + Android XML Views + Material Components (`com.google.android.material:material:1.12.0`)  
> **Localization:** Japanese (`values-ja`) & English (`values`) first-class support

---

## 1. Core Concept & Product Direction (concept-driven-ux)

### 1.1 Concept Statement (1文コンセプト)
> **コマンドに慣れていないスマホ利用者**が、**Termux Baby を開いた瞬間**に、**英語や黒い画面に怯えず日常の言葉のカードをタップするだけ**で、**ツール導入・ファイル確認・ネット確認・メモ・Python体験などを安心して完遂し、「自分でもできた！」と感じられる体験**。

### 1.2 Concept Decomposition
| 要素 | 定義 |
| --- | --- |
| **誰 (Who)** | CLIコマンドを普段使わない初心者・スマホユーザー（＋必要な時だけ通常ターミナルも使いたい人） |
| **場面 (When)** | スマホでちょっとしたツールを試したい・ファイルや通信を確認したい・プログラミングに触れたいとき |
| **行為 (What)** | 専門用語を読まず、アイコンと日本語/平易な英語のワンタップカードを選び、やさしい結果を見る |
| **状態・気持ち (Feeling)** | 「怖くない」「読まなくてもわかる」「1回押したらちゃんとできた！」という安心感と小さな成功体験 |
| **最優先する価値 (Core Value)** | **3秒で次に押すものがわかる直感性**と、**裏側で本物のTermuxの力を失わない安心感** |

### 1.3 Technical & Architectural Constraints (Verified from Codebase)
1. **Package ID & Bootstrap Constraint (`applicationId`)**:
   - Termuxの公式bootstrapバイナリは `$PREFIX = /data/data/com.termux/files/usr` がハードコードされている（`app/build.gradle`, `strings.xml` の `bootstrap_error_not_primary_user_message` 参照）。
   - したがって `applicationId` / `TERMUX_PACKAGE_NAME` は `com.termux` を維持し、**アプリ表示名 (`Termux Baby`)・ランチャーアイコン・テーマ・UI体験**を全面リブランドする（将来の独自bootstrap再ビルド無しで全パッケージが即動作する最も安全な構成）。
2. **UI Stack Compatibility**:
   - 現行 `app/build.gradle` は純Java 8 + XML Views + `Material 1.12.0` (`MaterialCardView`, `MaterialButton`, `BottomSheetDialog`, `Chip`, `ViewPager`)。Kotlin/Compose を無理に導入せず、既存の Material 3 XML コンポーネントで軽量・高速・ビルド安定に実装する。
3. **Zero Regression on Pro Terminal Power**:
   - `TerminalView` / `TermuxService` / `TermuxSession` / `RunCommandService` は一切壊さない。
   - **「かんたんモード (Baby Mode)」**（デフォルト）と **「ターミナル (Pro Mode)」** を画面上部の1タップ切替ピルでいつでも往復できる二層構造にする。

### 1.4 Existing Termux Feature Evaluation (維持・変更・統合・削減)
同じ利用場面（初回起動〜最初の1タスク完遂）で現行Termuxの全要素を評価した結果：

| 現行機能 / 要素 | 現状の課題（初心者視点） | 判定 | Termux Baby での扱い |
| --- | --- | --- | --- |
| 真っ黒な全画面 `TerminalView` (`#000000`) | 起動直後にカーソルだけ点滅し「何をしていいか分からない」「怖い」 | **変更** | デフォルトは「かんたんモード」を表示。Proモード側も目に優しい温かみのあるダークスレート (`#171A21`)＋大きめ文字サイズに変更 |
| 起動時の英語MOTD長文（Wiki/Community/pkg案内） | 画面を埋め尽くす英語テキストの壁。最初の認知負荷が最大 | **削減（非表示）** | 初回セットアップ時に `~/.hushlogin` を生成してMOTDの壁を抑制（Proモードでもすっきり開始） |
| 下部の多機能 Extra Keys バー (`ESC / - HOME UP END PGUP...`) | 14個以上の記号キーが常時並び、文字入力の邪魔で圧迫感が強い | **変更（条件表示）** | Babyモードでは非表示。Proモードでも初心者向けにデフォルトOFF（またはシンプル1行化）、必要時にトグルで表示 |
| 左ドロワー（セッション一覧 + Keyboard + New Session） | セッション番号 `[1]` や長押しメニュー `Failsafe` など専門用語だらけ | **変更・整理** | 「かんたんモード」の主導線からは分離し、Proモードの補助UIとして整理。ラベルも平易化 |
| コンテキストメニュー（12項目：Kill process, Reset, Report等） | 普段使わない専門操作が1画面に並ぶ | **維持（Pro限定）** | Proモードの長押し時のみ表示。Babyモードでは触れない |
| `TerminalSession` / `TermuxService` / `pkg` 実行基盤 | 本物のLinux環境を動かせるコア価値 | **維持** | 裏側でそのまま活用。Babyモードのカードはすべて実セッションへ安全なコマンドを送る |
| 英語のみの `strings.xml` | 日本語話者にとって設定やエラーが理解不能 | **変更（多言語化）** | `values-ja/strings.xml` を新設し、日本語・英語の両方で完全な平易表現を提供 |

---

## 2. Cognitive Load Reduction & Information Triage (minimal-information-design)

### 2.1 Concept Triage (必須 / 支援 / ノイズ) — Home Screen (起動直後)

| 必須 ＋ 支援（残す） | 判定文 / 助け先 | ノイズ（消す / 隔離） | 種別・行き先 |
| --- | --- | --- | --- |
| **[必須] かんたんアクションカード (6枚)** | これが無いと「コマンドを打たずにやりたいことを実行する」行為が成立しない | **英語MOTDテキストの壁** | **阻害ノイズ → 削除** (`~/.hushlogin`) |
| **[必須] やりたいこと検索 / ヘルパーボタン** | これが無いと6枚以外の操作をコマンド無しで探せない | **14個の記号Extra Keys常時表示** | **阻害ノイズ → Babyでは削除 / ProでBランク化** |
| **[必須] モード切替ピル (かんたん / ターミナル)** | これが無いと初心者体験と本物のTermuxの両立が成立しない | **生プロンプト `$ ` と点滅カーソル** | **阻害ノイズ → Proモードへ隔離** |
| **[必須] 実行結果のやさしいシート** | これが無いと「できた！」の実感と安心感が成立しない | **生の英語エラーログの丸投げ** | **阻害ノイズ → やさしい翻訳＋折りたたみ詳細(B)へ** |
| **[支援] 現在の状態1行バッジ（準備完了など）** | 必須の「アクションカード」が今押せる状態かを助ける | **セッション番号・PID・Failsafe表記** | **無関係ノイズ → Proドロワー(C)へ隔離** |
| **[支援] 設定・ヘルプアイコン (右上1つ)** | テーマ切替やオンボーディング再表示を助ける | **長文の説明段落・チュートリアル再掲** | **重複ノイズ → 削除**（オンボーディングと二重説明しない） |

- **ノイズ率の変化（起動直後の画面）**：
  - 現行 Termux：常時表示要素のほぼすべてが初心者にとって阻害ノイズ（MOTD壁＋生プロンプト＋14個以上のExtra Keys＋専門ドロワー）→ **ノイズ率 約 85%**
  - Termux Baby（かんたんモード）：必須4種＋支援2種のみ、阻害ノイズ 0 → **ノイズ率 0%**

### 2.2 Display Priority Ranks (A / B / C)
- **Rank A（常時表示 — 画面の3割以下の認知密度）**:
  1. トップバー：アプリ名 / モード切替ピル `[ かんたん | ターミナル ]` / 設定ボタン（1つ）
  2. やさしい挨拶1行（例：「なにをしようか？」／英語: `"What shall we do?"`）※説明文は置かない
  3. **ワンタップ・アクションカード（2列 × 3行 = 6枚）**：大きなアイコン＋短い動詞タイトル（2〜6文字）＋極短サブ（最大10文字）
  4. 画面下部（親指ゾーン）：プライマリピルボタン **「ことばで探す / Command Helper」**
- **Rank B（操作後に展開・折りたたみ）**:
  - コマンド実行中・実行後の **やさしい結果ボトムシート**（状態アイコン＋わかりやすい要約＋「できた！」反応）
  - 結果シート内の **「くわしい文字を見る (Raw Output)」折りたたみアコーディオン**（初期状態は閉じている）
  - コマンドヘルパー内のカテゴリ別ガイド＆やさしい引数入力
- **Rank C（別画面・Proモードへ集約）**:
  - フルターミナル画面（Pro Mode）
  - セッション管理ドロワー・Extra Keys・詳細設定（Logging / Debugging / Terminal I/O）

### 2.3 Anti-Duplication & 8-Principle Guardrails
- **同じことを2回言わない**：オンボーディングで伝えた使い方をホーム画面のバナーで繰り返さない。カード見出し（例：「ファイルを見る」）の直下に「ファイルの一覧を見ることができます」のような言い直し説明を置かない。
- **3秒テスト / 無読テスト基準**：ホーム画面を3秒見て「ボタンを選べば何かできる」と分かり、文字を読まなくてもアイコンと色で6つの行動が識別できること。
- **親指ルール（モバイル片手操作）**：最もよく使う「ことばで探す」ボタンと、ボトムシートの「実行する / とじる」ボタンは画面下部に配置する。危険な操作（プロセス強制終了・リセット）はBabyモードに置かない。

---

## 3. Art Direction & Visual Design System (meaningful_app_design)

### 3.1 Art Direction (`アート方針`)
- **世界の言葉（雰囲気キーワード 3つ）**:
  1. **やわらかい (Soft)** — 強い黒白コントラストを避け、丸みと余白で視線の緊張をほどく
  2. **安心 (Reassuring)** — 押しても壊れない・失敗してもやさしく教えてくれる信頼感
  3. **すぐわかる (Instant Clarity)** — 1画面1主役、アイコン＋短い言葉で迷わせない
- **やらない雰囲気（Anti-patterns）**:
  - ハッカー風の真っ黒×ネオングリーン
  - 開発者ツール風の詰め込み・極小フォント
  - 幼児向けおもちゃのような過度な原色・うるさい装飾（**「赤ちゃんでも使える」＝「誰でも直感で迷わない究極の優しさ」**として品よく仕上げる）

### 3.2 Color Tokens (Light & Warm Dark)
色数を絞り、本文コントラスト比 **4.5:1 以上** を保証する：

| Token Role | Light Theme (Warm Paper) | Dark Theme (Warm Slate) | 用途・理由 |
| --- | --- | --- | --- |
| `baby_bg` | `#F6F8F7` | `#161920` | まぶしすぎない紙色 / 目に優しい温かな濃紺スレート（真っ黒 `#000000` を廃止） |
| `baby_surface` | `#FFFFFF` | `#202530` | アクションカード・ボトムシートの面 |
| `baby_surface_subtle` | `#ECF2F0` | `#29303E` | モード切替トラック・アイコン丸背景・折りたたみコード枠 |
| `baby_primary` | `#248A73` | `#52C7AC` | 主役色（1色）：安心感と前進を表す落ち着いたミントティール |
| `baby_on_primary` | `#FFFFFF` | `#0C2620` | 主役ボタン上のテキスト・アイコン |
| `baby_text_primary` | `#1A2226` | `#EEF2F5` | 見出し・カードタイトル（高コントラスト） |
| `baby_text_secondary` | `#556268` | `#9CA8B3` | 補助ラベル・キャプション |
| `baby_border` | `#DDE5E2` | `#2E3646` | 1dp の繊細な輪郭線 |
| `baby_success` | `#248A73` | `#52C7AC` | 完了・「できた！」の印（主役色と統一し色数を増やさない） |
| `baby_warning` | `#C97A16` | `#F0B254` | 確認・注意（ネットワーク未接続など） |
| `baby_error` | `#C94A4A` | `#EE7A7A` | 失敗時のやさしい案内（威圧的な真っ赤を避ける） |

### 3.3 Shape, Typography & Motion Vocabulary
- **角丸（3種に固定）**: カード `18dp` / ボトムシート上部 `24dp` / ボタン・ピル `999dp` (full pill) または `14dp`
- **余白リズム**: `8dp` 基準（画面端 `20dp`、カード間 `12dp`、カード内余白 `16dp`）
- **文字サイズ階層（4段に固定）**:
  - 画面見出し: `22sp` Bold
  - カード見出し / シート見出し: `16sp` SemiBold
  - 本文 / ボタン: `14sp` Medium
  - 補足 / コマンドプレビュー: `12sp` Regular (`monospace` は折りたたみ内のコマンド表示のみ)
- **Pro ターミナルのデフォルト改善**:
  - 既定フォントサイズを現行の極小サイズから読みやすいサイズ（最低 `14sp` 相当、既存Preferenceより大きめの初期値）へ引き上げ。
  - 既定カラーを `#161920` 背景 / `#EEF2F5` 文字のソフトダークに設定（ユーザー独自の `~/.termux/colors.properties` がある場合はそちらを尊重）。
- **動きの性格**:
  - 意味のある動きのみ（押下の軽いバウンス `200ms`、カード入場スタガー `60ms` 刻み、ボトムシート展開）。
  - `Settings.Global.ANIMATOR_DURATION_SCALE == 0` の場合はアニメーションをスキップして即時表示。

---

## 4. Screen-by-Screen UX Design

### 4.1 Screen Meaning Matrix (画面ごとの意味)
| 画面 | 一番見せたいもの（主役） | どう思ってほしいか（狙う気持ち） | 主な実現手段 |
| --- | --- | --- | --- |
| **初回オンボーディング** | 押すだけで使える3ステップの安心感 | 「これなら私でも使えそう！早く押してみたい」 | 1ページ1主張・3択の悩み選択→マッチ宣言・触覚＋称賛 |
| **かんたんホーム (Baby Mode)** | 6つのワンタップ・アクションカード | 「どれを押せばいいか一目でわかる」 | 2×3の大きなカード・最小限の文字・余白 |
| **実行結果シート (Runner Sheet)** | 「いま動いている／できた！」のわかりやすい結果 | 「ちゃんと動いた！壊れてない！」 | 状態アイコン＋日本語/英語の要約＋生ログ折りたたみ |
| **ことばで探す (Command Helper)** | やりたいこと別のコマンドカード＋やさしい入力欄 | 「コマンドを暗記しなくても大丈夫」 | カテゴリチップ＋平易な入力フォーム＋実行前プレビュー |
| **ターミナル (Pro Mode)** | クリーンで読みやすい本物のシェル画面 | 「本格作業もそのまま快適にできる」 | 温かなダーク配色・大きめ文字・上部1タップ復帰ピル |

### 4.2 No-Command-Needed UX: The 6 Core Action Cards (Baby Home)
すべて **ワンタップで安全に実行** でき、危険な破壊コマンドは含めない。各カードは裏側で本物の `TerminalSession`（または安全なバックグラウンドコマンド実行＋履歴連携）を使う：

| # | 日本語タイトル | 英語タイトル | アイコン | 実行コマンド（裏側） | やさしい結果の伝え方 |
| --- | --- | --- | --- | --- | --- |
| 1 | **ファイルを見る** | **See Files** | フォルダ | `ls -la --color=never` | ファイル数とフォルダ名をやさしくリスト表示（空なら「まだファイルはありません」） |
| 2 | **ツールを入れる** | **Install Tools** | 宝箱/プラス | ガイド付き選択シート（Python / Git / Nano / Tree 等を1タップで `pkg install -y <pkg>`） | 初心者向けおすすめツールを説明付きで1タップ導入 |
| 3 | **ネット確認** | **Check Network** | Wi-Fi/地球 | `ping -c 3 1.1.1.1` (失敗時はやさしくオフライン案内) | 「インターネットにつながっています（応答 ○○ms）」/「つながっていないようです」 |
| 4 | **メモを書く** | **Quick Note** | 鉛筆/メモ | やさしいテキスト入力シート → `~/notes/` に日時付き `.txt` 保存 | 「メモを保存しました（`~/notes/...`）」＋保存済みメモ一覧 |
| 5 | **スマホ情報** | **Device Info** | スマホ/情報 | `uname -m && df -h $HOME` | 空き容量・環境情報をわかりやすい2〜3行に要約 |
| 6 | **Pythonを試す** | **Try Python** | きらきら/コード | Python導入済みなら簡単な対話/サンプル実行シート、未導入なら1タップで導入案内 | 「こんにちは！」と画面に出る最初のプログラミング成功体験 |

### 4.3 Friendly Error Translator (`BabyErrorTranslator`)
コマンドがエラーコード（非0）やよくある失敗メッセージを返したとき、英語のエラー文をそのまま突きつけず、**人間向けの1文＋次の行動ボタン** に翻訳する：
- `command not found` → 「まだこの道具が入っていないようです」＋ `[ 今すぐ入れる ]` ボタン
- `Temporary failure resolving` / `Network is unreachable` → 「インターネットにつながっていないようです。Wi-Fiや通信を確認してみてください」
- `Permission denied` → 「この場所を開く許可がありません。ホームフォルダで試してみてください」
- `No such file or directory` → 「そのファイルやフォルダが見つかりませんでした」
- その他 → 「うまくいきませんでした。もう一度試すか、詳しい出力を確認できます」＋折りたたみの生出力

### 4.4 Plain-Language Command Helper (`BabyCommandHelperSheet`)
ホーム下部の **「ことばで探す / Find an action」** ボタンから開くボトムシート：
- 検索バー（「ファイルをさがす」「文字をかぞえる」「フォルダをつくる」など日常語で絞り込み）
- カテゴリチップ（すべて / ファイル / ネット / 学習 / ツール）
- 各項目は「やりたいこと（日常語）」「実際に動くコマンド（小さく薄く表示・学びにもなる）」をセットで表示し、引数が必要なもの（例：フォルダ名・検索キーワード）は**わかりやすい日本語入力欄**を出してから安全にクォートして実行。

### 4.5 Gentle First-Run Onboarding (`mobile_onboarding` 準拠)
初回起動時のみ表示（スキップ可能・設定からいつでも再表示可能・完了フラグを `SharedPreferences` に保存）。
**絶対ルール（1ページ1主張・質問と結果の分離・3択・窓ドット・押したら褒める）** を厳守した **7ページ構成（所要約45〜60秒）**：

| Page | 役割 | 見出し (JA / EN) | 主張・UI要素 | 意味のあるアニメーション & 触覚 |
| --- | --- | --- | --- | --- |
| 1 | **フック / アイデンティティ** | 「コマンドいらずのやさしいターミナル」 / `"A gentle terminal, no commands needed"` | 温かなアイコン＋1行補足のみ | アイコンの静かなフェードイン (`300ms`) |
| 2 | **選択 (3択・次へボタン無し)** | 「いま一番やってみたいことは？」 / `"What would you like to try first?"` | 3択カード：①「ツールやPythonをさわってみたい」 ②「ファイルやスマホをかんたんに確認したい」 ③「まだわからない・まずは見てみたい」(逃げ道) | タップで弾み (`250ms`)＋`HapticFeedbackConstants.CONFIRM`＋具体的な称賛1文 → `950ms` 余韻 → 自動遷移 |
| 3 | **マッチ宣言 (結果専用)** | 「そんなあなたに、Termux Baby がぴったり」 / `"Termux Baby is made for you"` | 選んだ言葉をそのまま映し返し、対応する強みを1つだけ提示（逃げ道選択時は「ゆっくり試しながら使えます」と穏やかに返す） | 見出し → アイコン → 宣言の順に `150ms` ずつ段階入場 |
| 4 | **ステップ 1/3** | 「1. カードを選ぶ」 / `"1. Pick a card"` | アイコン付きの大きなカードを押すだけの図解 | カード1枚が軽く浮く (`250ms`) |
| 5 | **ステップ 2/3** | 「2. やさしい結果を見る」 / `"2. See clear results"` | 難しい英語ではなく、わかりやすい結果が出る図解 | 完了チェックが描かれるように表示 |
| 6 | **ステップ 3/3** | 「3. いつでも本格画面へ」 / `"3. Switch to Pro anytime"` | 上部のピルで通常ターミナルに切り替えられる図解 | ピルのトグル移動アニメーション |
| 7 | **接続 (CTA)** | 「まずは1つ、押してみよう」 / `"Let's try your first tap"` | 主役ボタン：「かんたんホームをひらく」(押した先を明示)＋選んだ目的に合わせてホームの最初のカードをハイライト | 完了時の軽いお祝いアニメーション＋成功触覚 (`CONFIRM`) |

- **進捗表示**：最大5つの **窓ドット**（総ページ数「3/7」のような数字は出さない）。
- **スキップ**：右上に常に中立な「スキップ / Skip」ボタン（称賛なし・責めない）。

---

## 5. UI Catalog (Reusable Components)

| Component | Layout / Class | Role & States |
| --- | --- | --- |
| **Mode Switcher Pill** | `baby_top_bar.xml` | Baby / Pro モードを1タップ切替（Active / Inactive / Pressed） |
| **Action Card** | `item_baby_action_card.xml` | 2列グリッドのワンタップカード（Default / Highlighted from onboarding / Pressed） |
| **Friendly Runner Sheet** | `sheet_baby_runner.xml` | 実行中（ローディング）・成功・やさしいエラー・折りたたみ生ログ |
| **Command Helper Sheet** | `sheet_baby_helper.xml` | 検索＋カテゴリチップ＋日常語レシピ一覧＋引数入力ダイアログ |
| **Quick Note Sheet** | `sheet_baby_note.xml` | タイトル不要・1タップ保存の超シンプルメモ作成＋一覧 |
| **Tool Installer Sheet** | `sheet_baby_installer.xml` | Python / Git / Nano / Tree 等のおすすめツール1タップ導入 |
| **Onboarding Window Dots** | `WindowDotsIndicatorView.java` | 最大5ドットがスライドする窓ドット進捗表示 |

---

## 6. Concrete File-Level Implementation Plan (Parallel Workstreams)

並列の実装ワーカーが**ファイル競合ゼロ**で同時に開発できるよう、以下の **4つの独立ワークストリーム** に分割する。
Workstream A / B / C は互いに一切ファイルを共有せず完全並列で実装可能。Workstream D は A / B / C 完了後に `TermuxActivity` と `AndroidManifest` へ最小限の結線を行う。

### Workstream A: `branding-and-theme` (Branding, Design Tokens, Localization)
**Owns (No overlap with B/C/D):**
- `app/src/main/res/values/baby_colors.xml` *(new)* — Light テーマ用デザイントークン（Warm Paper + Mint Teal）
- `app/src/main/res/values-night/baby_colors.xml` *(new)* — Dark テーマ用デザイントークン（Warm Slate + Soft Mint）
- `app/src/main/res/values/baby_styles.xml` *(new)* — カード、ピルボタン、ボトムシート、文字階層スタイル
- `app/src/main/res/values/baby_strings.xml` *(new)* — 英語の Baby Mode / Onboarding / Helper / Error 文字列
- `app/src/main/res/values-ja/strings.xml` *(new)* — 既存 Termux 基本UIの日本語訳（設定・ドロワー・通知・エラー等）
- `app/src/main/res/values-ja/baby_strings.xml` *(new)* — 日本語の Baby Mode / Onboarding / Helper / Error 文字列
- `app/src/main/res/values/strings.xml` *(edit: `TERMUX_APP_NAME` entity `"Termux"` → `"Termux Baby"` のみ)*
- `termux-shared/src/main/res/values/strings.xml` *(edit: `TERMUX_APP_NAME` entity `"Termux"` → `"Termux Baby"` のみ)*
- `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` *(edit: `TERMUX_APP_NAME = "Termux Baby"` のみ、`TERMUX_PACKAGE_NAME` は `com.termux` 維持)*
- `app/build.gradle` *(edit: `manifestPlaceholders.TERMUX_APP_NAME = "Termux Baby"` および APK出力ファイル名プレフィックスを `termux-baby_` に変更)*
- `app/src/main/res/drawable/ic_baby_*.xml` *(new)* — 統一タッチ（24dp rounded vector）のアクションカード・モード切替アイコン群
- `app/src/main/res/drawable/baby_*.xml` *(new)* — ピル背景、カードリップル、オンボーディング用ソフトイラストベクター
- `app/src/main/res/drawable/ic_foreground.xml` & `app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml` *(edit)* — 温かみのある Termux Baby アダプティブアイコン

### Workstream B: `baby-home-and-actions` (Baby Mode Home, Action Cards, Runner, Helper, Error Translator)
**Owns (All new files under `com.termux.app.baby` + `baby_*` / `sheet_baby_*` layouts):**
- `app/src/main/java/com/termux/app/baby/BabyPreferences.java` *(new)* — Baby/Proモード状態、オンボーディング完了・選択目標、初回成功フラグの管理 (`SharedPreferences`)
- `app/src/main/java/com/termux/app/baby/BabyAction.java` *(new)* — アクション定義モデル（ID, タイトルRes, サブRes, アイコンRes, 種別, コマンド, 要約タイプ）
- `app/src/main/java/com/termux/app/baby/BabyActionCatalog.java` *(new)* — 6つの主役カード＋Command Helper用レシピ集（引数付き安全エスケープ対応）
- `app/src/main/java/com/termux/app/baby/BabyCommandExecutor.java` *(new)* — `$PREFIX/bin/sh` を使った安全な非同期コマンド実行（タイムアウト・キャンセル対応）＋ Proターミナルセッションへコマンドを送るブリッジ
- `app/src/main/java/com/termux/app/baby/BabyOutputFormatter.java` *(new)* — 実行結果（ファイル一覧、ping、df/uname、Python等）を初心者向けの平易な要約に変換
- `app/src/main/java/com/termux/app/baby/BabyErrorTranslator.java` *(new)* — よくあるCLIエラーを平易な日本語/英語ヒント＋復旧アクションへ翻訳
- `app/src/main/java/com/termux/app/baby/BabyCommandRunnerSheet.java` *(new)* — 実行中・成功・やさしいエラー・折りたたみ生ログを表示する `BottomSheetDialog`（「Proターミナルで開く」導線付き）
- `app/src/main/java/com/termux/app/baby/BabyCommandHelperSheet.java` *(new)* — 「ことばで探す」検索・カテゴリ・引数入力付きヘルパーシート
- `app/src/main/java/com/termux/app/baby/BabyQuickNoteSheet.java` *(new)* — `~/notes/` にワンタップ保存＆閲覧できるやさしいメモシート
- `app/src/main/java/com/termux/app/baby/BabyToolInstallerSheet.java` *(new)* — Python / Git / Nano / Tree を説明付きで1タップ導入するシート
- `app/src/main/java/com/termux/app/baby/BabyModeController.java` *(new)* — Baby Home画面の初期化、6枚カードのバインド、オンボーディング選択に応じたカードハイライト、Pro切替コールバック
- Layouts *(all new)*:
  - `app/src/main/res/layout/view_baby_home.xml`
  - `app/src/main/res/layout/item_baby_action_card.xml`
  - `app/src/main/res/layout/sheet_baby_runner.xml`
  - `app/src/main/res/layout/sheet_baby_helper.xml`
  - `app/src/main/res/layout/item_baby_helper_recipe.xml`
  - `app/src/main/res/layout/sheet_baby_note.xml`
  - `app/src/main/res/layout/sheet_baby_installer.xml`

### Workstream C: `baby-onboarding` (Gentle 7-Page First-Run Onboarding)
**Owns (All new files under `com.termux.app.baby.onboarding` + `activity_baby_onboarding*.xml`):**
- `app/src/main/java/com/termux/app/baby/onboarding/BabyOnboardingActivity.java` *(new)* — 7ページ構成の `ViewPager` ホスト、スキップ・戻る・結果映し返し・完了保存・OS「動きを減らす」対応
- `app/src/main/java/com/termux/app/baby/onboarding/BabyOnboardingPagerAdapter.java` *(new)* — 1ページ1主張のページ生成、3択の「押したら褒める」弾み＋触覚＋称賛→余韻→自動遷移の段階制御
- `app/src/main/java/com/termux/app/baby/onboarding/WindowDotsIndicatorView.java` *(new)* — 最大5ドットの窓スライド進捗インジケータ（数字表示なし）
- Layouts *(all new)*:
  - `app/src/main/res/layout/activity_baby_onboarding.xml`
  - `app/src/main/res/layout/item_baby_onboarding_info.xml`
  - `app/src/main/res/layout/item_baby_onboarding_choice.xml`

### Workstream D: `termux-activity-integration` (Surgical Integration into Core Termux UI)
**Runs after A, B, C are ready (owns all edits to existing core Termux files):**
- `app/src/main/AndroidManifest.xml` — `BabyOnboardingActivity` の登録
- `app/src/main/res/layout/activity_termux.xml` — 上部に薄い `baby_top_bar`（モード切替ピル `[ かんたん | ターミナル ]` ＋設定ボタン）を追加し、`DrawerLayout` 内のメイン領域を `FrameLayout` でラップして `TerminalView` と `<include layout="@layout/view_baby_home" />` を切り替え可能にする
- `app/src/main/java/com/termux/app/TermuxActivity.java` —
  - 初回起動時に `BabyOnboardingActivity` を起動（完了済みならスキップ）
  - `BabyModeController` の初期化と Baby/Pro モード切替（Baby時はソフトキーボードとExtra Keysを非表示、Pro時は通常Termux動作に完全復帰）
  - 初回bootstrap完了時に `~/.hushlogin` を作成し、MOTDの壁を抑制
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java` —
  - ユーザー独自の `colors.properties` が無い場合、真っ黒 `#000000` ではなく温かなダークスレート `#161920` 背景＋ `#EEF2F5` 文字を既定適用
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java` —
  - Baby Mode 表示中はソフトキーボードの自動ポップアップを抑止し、Pro Mode 切替時のみ通常動作させる
- `app/src/main/res/xml/root_preferences.xml` & `SettingsActivity.java` (or `TermuxPreferencesFragment.java`) —
  - 設定画面の先頭に「かんたんガイドをもう一度見る (Show Welcome Guide)」と「起動モード (Baby / Pro)」を追加
- `app/src/test/java/com/termux/app/baby/` — `BabyOutputFormatter`, `BabyErrorTranslator`, `BabyActionCatalog` の単体テスト追加

---

## 7. Verification Plan & Unverified Boundaries

### 7.1 Automated & Code-Level Verification (Implemented by Swarm)
1. **Unit Tests (`./gradlew testDebugUnitTest`)**: `BabyErrorTranslator`, `BabyOutputFormatter`, `BabyActionCatalog` のコマンドエスケープ・要約・エラー翻訳を検証。
2. **Debug APK Build (`./gradlew assembleDebug`)**: 全リソース（`values` + `values-ja` + `values-night`）、レイアウト、Javaコードのコンパイル・リソースリンク成功を確認。
3. **Accessibility & Static Checks**: 全アイコンボタンへの `contentDescription` 付与、コントラスト比 4.5:1 以上、OSアニメーション無効時のフォールバック確認。
4. **Reachability Audit**: 畳んだ生ログ（1タップ）、Proターミナル（上部ピル1タップ）、設定（右上1タップ）、オンボーディング再表示（設定→1タップ）の到達ステップ数を検証。

### 7.2 Human-Required Verification (Must Remain Marked Unverified Until Tested on Device)
以下はAIの静的チェックやビルド成功だけでは完了扱いにせず、実機またはエミュレータで人間が確認する項目として明記する：
- **3秒テスト / 無読テスト**：初見ユーザーがBaby Homeを見て3秒以内に「何のアプリか・次に何を押すか」を言えるか。
- **オンボーディングのテンポと心地よさ**：3択押下時の弾み・触覚・余韻（`950ms`）→自動遷移の体感リズム。
- **実機での bootstrap 導入＋コマンド実行**：Android端末上で `pkg install`・`ping`・`Quick Note`・`Python` が実際に動作し、Proモードとの往復でキーボードや表示崩れが起きないこと。
