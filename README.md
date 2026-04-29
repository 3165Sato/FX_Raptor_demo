# FX_Raptor_demo

## 起動手順

1. PostgreSQL を起動します。

```bash
docker compose up -d
```

2. Spring Boot アプリを起動します。

```bash
./mvnw spring-boot:run
```

Windows PowerShell の場合:

```powershell
.\mvnw.cmd spring-boot:run
```

3. 動作確認をします。

- `http://localhost:18080/admin/orders`
- `http://localhost:18080/admin/trades`
- `http://localhost:18080/admin/positions`

初回起動時は `CommandLineRunner` で初期データを投入します。`Account` や `MarginRule` は `findBy...`、`Trade` は `count()`、`Position` は `exists` 相当の検索で重複投入を避けるため、アプリ再起動後も PostgreSQL 上のデータをそのまま利用できます。
