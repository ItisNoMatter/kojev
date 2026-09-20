# kojev 立ち上げ手順

プロジェクト初期の一度きりの手順。常時効くルールは `CLAUDE.md` にある。
フェーズが全て完了したら、このファイルは役目を終える（削除するか、履歴として残す）。

---

## 事前準備（人間側）

公式の TypeSafe エージェントスキルを入れておくと、Jev の設計指針が
セッションに読み込まれるので精度が上がる。

```
claude plugin marketplace add typesafe-ai/skills
claude plugin install typesafe@typesafe-ai
```

API キーは `console.typesafe.ai` で取得（早期アクセスのウェイトリストあり）。
キーが無くても Phase 5 までは進められる設計にしてある。

---

## Phase 1: 調査

**実装コードは1行も書かない。**

以下を読む：

1. `https://docs.typesafe.ai/llms.txt` — ドキュメント全体のインデックス
2. `https://docs.typesafe.ai/api` — HTTP API の正確なリクエスト / レスポンス形
3. `https://docs.typesafe.ai/primitives/choice` / `/primitives/score` / `/primitives/noul`
4. `https://docs.typesafe.ai/model-jaggedness/jev-1.13` — 既知の弱点。DSL 設計の判断材料になる
5. 公式 JavaScript SDK (`typesafe-ai/typesafe-sdk-js`) と Python SDK (`typesafe-ai/typesafe-sdk-python`) のソース

読んだ結果を `docs/api-notes.md` に出典 URL つきで書き出す。最低限、以下を含めること：

- エンドポイントと認証方式
- リクエスト JSON の完全な形（Choice / Score / Noul それぞれの質問定義を含む）
- レスポンス JSON の完全な形（確率分布、confidence の有無をプリミティブごとに）
- エラーレスポンスの形とステータスコード
- リトライに関する公式の指針
- モデルエイリアス（`jev-latest` / `jev-preview`）の扱いと、バージョン固定の方法
- レート制限、既知の制約

**`CLAUDE.md` の記述と実際の API が食い違っていたら、実 API を正とし、
食い違いを報告してから進むこと。** `CLAUDE.md` の修正も提案する。

このファイルは以降の実装の唯一の根拠になる。曖昧なまま次に進まない。

---

## Phase 2: 骨格

- Gradle 設定、KMP ターゲット、モジュール構成
- ktlint / API validation などの静的チェック
- CI（全ターゲットのビルドとテスト）

空のビルドが全ターゲットで通る状態になったら報告。

---

## Phase 3: ドメイン層

- Choice / Score / Noul の型
- `QuestionKey<T>` と結果の取り出し
- リクエスト / レスポンスのシリアライズ

`MockEngine` を使ったテストを同時に書く。モック用 JSON は `docs/api-notes.md` から起こす。

---

## Phase 4: DSL 層

`JevClient { }` と `decide { }`。**ここが本丸。**

書き味を実際のサンプルコードで確認しながら作ること。
`CLAUDE.md` のスケッチと違う形にするなら、実装前に理由とともに提案する。

---

## Phase 5: トランスポート

- リトライ（408 / 429 / 5xx、指数バックオフ + ジッター、`Retry-After`）
- エラー階層とリクエスト ID
- タイムアウト

ここまで API キー無しで到達できること。

---

## Phase 6: 仕上げ

- README（Jev が何であって何でないか / キーの置き場所の注意 / 既存クライアントとの違い / 非公式である旨）
- `examples/` を実際にビルド対象に含める形で用意（放置して腐らないように）
- Maven Central 公開設定
- `awesome-jev` 系リストへの PR（説明文に `jev` の文字列を含め、topics に `jev` `typesafe` `kotlin-multiplatform` を付ける）

---

## 進め方

**各フェーズの終わりで止まって報告し、承認を得てから次へ進む。**
まとめて一気に実装しない。

---

## 完了条件

- `CLAUDE.md` のスケッチ相当のコードが、キャストなし・`!!` なしでコンパイルでき、意図通り動く
- 全ターゲットで `./gradlew build` が緑
- API キー無しでテストが完走する
- README が上記の内容を満たしている
- サンプルコードがビルド対象に含まれている
