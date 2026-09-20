# kojev

TypeSafe AI の System One モデル **Jev** のための Kotlin Multiplatform クライアントライブラリ。

初回セットアップと開発フェーズの進め方は `docs/bootstrap.md` を参照。
Jev API の確定した仕様は `docs/api-notes.md` に記録されている（実装前に必ず読むこと）。

---

## このライブラリの存在理由

Jev の質問に対する答えが、`String` ではなく **利用者が定義した `enum` / `sealed` 型で返ること**。

これが唯一の差別化点である。既存の Java / Kotlin クライアントは文字列キーで引いてキャストする方式であり、
そこに並ぶだけの実装には価値がない。**この性質を損なう設計判断は却下すること。**

非目標：
- テキスト生成のラップ（Jev は生成しない）
- プロンプトテンプレート、エージェントフレームワーク
- 公式 SDK の 1:1 移植（`ufec/typesafe-sdk-kotlin` が既にやっている）

---

## 絶対に守ること

### 1. API 仕様を推測で書かない

Jev は 2026年9月15日公開で、**あなたの学習データに存在しない**。
エンドポイント、フィールド名、レスポンス形を記憶から書くことを禁止する。

根拠は `docs/api-notes.md` にある。そこに無いことが必要になったら、
`https://docs.typesafe.ai/llms.txt` から辿って調べ、**api-notes.md を更新してから**実装する。
出典を示せない実装にはコメントで印をつけること。

### 2. 3つのプリミティブを取り違えない

| 型 | 問い | 返り値 |
|---|---|---|
| **Choice** | 選択肢から1つ選ぶ（`instructions` + ラベル→説明のマップ） | ラベル、確率分布、confidence |
| **Score** | 2〜10段階のルーブリックで採点 | スコア、凡例、確率分布、confidence |
| **Noul** | 「この記述は真か？」 | **0〜1の確率そのもの。confidence フィールドは無い** |

- **Noul は Null 判定ではない。** 真偽の確率を返す質問である
- **Noul と2択 Choice は等価ではない。** しきい値を相互に流用しない
- Choice の criteria が空、Score のレベルが2未満はリクエスト時にエラーとする

### 3. 型安全性を崩さない

- `result[key]` は `QuestionKey<T>` を受けて `T` に対応した答えを返す
- **文字列キーでの取得 API は補助としても提供しない**（用意すると必ずそちらが使われる）
- レスポンスに欠損・型不一致・範囲外があれば、黙ってデフォルト値に落とさず例外にする
- しきい値のデフォルト値をライブラリが決めない。判断は利用者のもの

### 4. API キーの前提

公式 SDK はブラウザからの直接利用を禁止している。キーが露出するため。

- `baseUrl` の差し替えを一級の機能として扱う（プロキシ・ゲートウェイ経由を想定）
- **サンプルコードにキーをハードコードしない。** 環境変数から読む例だけを書く
- iOS / Android ターゲットが存在するのは、共通のドメイン型と DSL をアプリ側と共有するためであり、
  端末から直接 API を叩くためではない（BYOK アプリは例外。README で区別して書く）

### 5. 鍵なしでテストが通ること

Jev は早期アクセスでウェイトリスト制。**鍵が無いと開発が止まる構成にしない。**

- Ktor の `MockEngine` でネットワーク非依存にリクエスト生成とレスポンス解析を検証する
- モック用 JSON は `docs/api-notes.md` の実スキーマから起こす。想像で書かない
- 実 API を叩くテストは別ソースセットに分離し、`TYPESAFE_API_KEY` がある時だけ実行（無い時はスキップ、失敗ではない）
- `jvmTest` だけ通った状態を「テスト済み」と呼ばない。全ターゲットで実行する

---

## 技術スタック

- Kotlin 2.0+ / Coroutines
- Ktor Client (`ktor-client-core`) + `ktor-serialization-kotlinx-json`
- kotlinx.serialization

ターゲット: `jvm()` / `androidTarget()` / `iosArm64()` / `iosSimulatorArm64()` / `iosX64()`
将来 `js(IR)` / `wasmJs` を追加できるよう、プラットフォーム固有 API を `commonMain` に漏らさない。

**Ktor エンジンはライブラリ側で固定しない。** `ktor-client-okhttp` 等を依存に持たず、利用者が注入する。

トランスポート要件：
- リトライは 408 / 429 / 5xx を対象に指数バックオフ + ジッター、`Retry-After` を尊重。設定可能にする
- エラーは HTTP ステータスごとのサブクラスに分け、リクエスト ID を保持する
- `JevClient` はスレッドセーフ。アプリに1つ作って共有する前提。`close()` で自前生成した HTTP クライアントを解放
- タイムアウトは1試行あたりの値

---

## 目標とする DSL

意図を伝えるためのスケッチであり、逐語的な仕様ではない。
同じ性質（型安全・宣言的・Compose 的な読み心地）を満たすなら、より良い形を提案してよい。
提案する場合は実装前に相談すること。

```kotlin
val jev = JevClient {
    apiKey = System.getenv("TYPESAFE_API_KEY")
    baseUrl = "https://api.typesafe.ai"
    model = "jev-latest"
    timeout = 10.seconds
    engine = CIO.create()
}

enum class Intent { REFUND, TECHNICAL_SUPPORT, GENERAL_INQUIRY }

// 質問は decide ブロックの外で、値として持てること
val intentQ = choice<Intent>("intent") {
    instructions = "この問い合わせを担当する部署を選ぶ"
    Intent.REFUND describedAs "返金・キャンセルの要求"
    Intent.TECHNICAL_SUPPORT describedAs "不具合・技術的な問題"
    Intent.GENERAL_INQUIRY describedAs "上記以外の一般的な質問"
}

val angryQ = noul("is_angry") {
    instructions = "顧客は怒りを表明しているか"
    whenTrue = "明確な苛立ち・強い語調がある"
    whenFalse = "中立または穏やか"
}

val urgencyQ = score("urgency") {
    instructions = "対応の緊急度"
    level(1, "急がない")
    level(3, "今日中が望ましい")
    level(5, "即時対応が必要")
}

val result = jev.decide {
    state("注文をキャンセルして今すぐ返金してほしい")
    ask(intentQ, angryQ, urgencyQ)
}

val intent: Intent = result[intentQ].value          // Intent 型で返る。キャスト不要
val dist: Map<Intent, Double> = result[intentQ].probabilities
val conf: Double = result[intentQ].confidence

val angry: Double = result[angryQ]                   // 確率そのもの。.confidence は生やさない
val urgency: Int = result[urgencyQ].value
```

補足：
- Choice のラベルは `enum` のほか `sealed interface` とも結びつけられること
- API 上のラベル文字列と Kotlin 側の識別子のマッピングは `@SerialName` 等で明示できるようにする
- confidence ゲーティングのヘルパーは用意してよい（例: `result[intentQ].orNull(minConfidence = 0.8)`）。
  ただしデフォルトのしきい値は設けない

1リクエスト内の複数質問は同じ state に対して並列評価される。
これが Jev の主用途なので、DSL は「複数質問を一度に投げる」ことを自然な形にすること。

---

## 命名

- ライブラリ名は `kojev`（Maven: `io.github.itisnomatter:kojev`）
- コード上の識別子は `kojev` に揃えない。`JevClient` / `jev.decide { }` のままでよい
  （Koin が `startKoin`、Ktor が `HttpClient` を名乗るのと同じ）

---

## 法務・礼儀

- ライセンスは MIT
- 公式 SDK のコードを参照・移植した場合、上流の著作権表示を `NOTICE` に残す
- 「TypeSafe」「Jev」は同社の商標。README に
  「本プロジェクトは非公式であり、TypeSafe AI, Inc. と提携・承認関係にない」と明記する
- 公式が将来 Kotlin SDK を出す可能性を前提に、名前空間を公式と衝突させない

---

## あなたへの指示

- 不明点を推測で埋めない。ドキュメントを読むか、質問する
- 「それっぽい」コードより、根拠のあるコードを書く
- このドキュメントの記述に誤りを見つけたら、黙って従わずに指摘すること
