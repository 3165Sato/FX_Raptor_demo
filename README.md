# Java 四則演算アプリ

Java で作成したシンプルな四則演算（足し算・引き算・掛け算・割り算）アプリです。

## 必要環境

- Java 17 以上
- Maven 3.8 以上

## 実行方法

### 1. テスト実行

```bash
mvn test
```

### 2. コマンドライン引数で実行

```bash
mvn -q exec:java -Dexec.mainClass="com.example.calculator.CalculatorApp" -Dexec.args="10 + 2"
```

### 3. 対話モードで実行

```bash
mvn -q exec:java -Dexec.mainClass="com.example.calculator.CalculatorApp"
```

## 使い方

- 引数ありの場合: `<数値> <演算子> <数値>`
  - 例: `10 + 2`
- 引数なしの場合: 対話モードで順番に入力
- 対話モードでは 1 つ目の数値入力時に `q` を入力すると終了
