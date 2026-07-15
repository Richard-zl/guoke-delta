# 测试环境搭建（test.guokegames.online）

正式：`guokegames.online` → Java **8080** → `delta_game`  
测试：`test.guokegames.online` → Java **8081** → `delta_game_test`

---

## 一、服务器操作（腾讯云网页终端 / SSH）

### 1. 创建目录

```bash
mkdir -p /www/wwwroot/dist-test
mkdir -p /www/data/upload-test
chmod -R 755 /www/data/upload-test
```

### 2. 创建测试数据库

在 **宝塔 → 数据库** 中：

1. 添加数据库：`delta_game_test`，字符集 `utf8mb4`
2. 访问权限：与正式库相同用户（如 `delta_game`）勾选该库
3. **导入** 项目根目录 `delta_game.sql`（与正式库数据隔离）

命令行方式（密码按宝塔实际修改）：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS delta_game_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
# 若正式用户是 delta_game，授权：
mysql -u root -p -e "GRANT ALL ON delta_game_test.* TO 'delta_game'@'localhost'; FLUSH PRIVILEGES;"
```

导入 SQL（上传 sql 到服务器后）：

```bash
mysql -u delta_game -p delta_game_test < /path/to/delta_game.sql
```

### 3. 修改测试配置并打包（在你 Windows 电脑）

编辑 `delta-game/delta-admin/src/main/resources/application-test.yml`：

- `spring.datasource.password` 改成与宝塔 MySQL 一致
- 若需测小程序登录：`wx.miniapp.enabled: true`

```powershell
cd f:\xzl_dev\guoke_delta\delta-game
mvn clean package -DskipTests -pl delta-admin -am
```

上传 `delta-admin\target\delta-admin.jar` 到服务器：

```text
/www/wwwroot/delta-admin-test.jar
```

### 4. 宝塔添加第二个 Java 项目

| 配置项 | 值 |
|--------|-----|
| 项目路径 | `/www/wwwroot/delta-admin-test.jar` |
| JDK | 17 |
| 端口 | **8081** |
| 启动参数 | `--spring.profiles.active=test` |

保存并启动。验证：

```bash
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8081/doc.html
```

应返回 `200` 或 `302`。

### 5. 宝塔添加站点 test.guokegames.online

1. **网站** → 添加站点 → 域名：`test.guokegames.online`
2. 根目录：`/www/wwwroot/dist-test`
3. **SSL** → Let's Encrypt → 申请
4. 站点 → **配置文件**，在 `server { ... }` 内加入（或合并到已有 HTTPS server）：

```nginx
    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8081/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /ws/chat {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400;
    }

    location /file/ {
        alias /www/data/upload-test/;
        expires 30d;
        access_log off;
    }
```

5. 保存并重载 Nginx

### 6. 验证

- 浏览器：`https://test.guokegames.online/api/doc.html`
- 正式站 **不要动** 8080 项目

---

## 二、环境对照

| 端 | 正式 | 测试 / 本地 dev |
|----|------|-----------------|
| 管理后台 | `npm run build` → guokegames.online | `npm run dev` / `build:staging` → **test.guokegames.online** |
| 小程序 | `build:mp-weixin` + .production | `dev:mp-weixin` / `build:mp-weixin:staging` → test API |
| H5 | `build:h5` + .production | `dev:h5` → test API（.env.development） |

本地 dev 已配置 `VITE_APP_ENV=test`，三端界面会显示 **测试环境** 标识。

## 三、本机构建测试前端

测试/staging/dev 构建会在界面显示 **橙色「测试环境」条**（管理后台顶栏 + 登录页；小程序/ H5 顶栏 + 导航栏变色 + 标题【测试】前缀）。

### PC 管理后台

```powershell
cd f:\xzl_dev\guoke_delta\delta-admin-ui
npm install
npm run build:staging
```

将 `dist` 目录内所有文件上传到服务器 `/www/wwwroot/dist-test/`。

### 微信小程序（体验版）

```powershell
cd f:\xzl_dev\guoke_delta\delta-mp
npm install
npm run build:mp-weixin:staging
```

用微信开发者工具打开 `delta-mp\unpackage\dist\build\mp-weixin`，上传为**体验版**。  
微信公众平台增加合法域名：`https://test.guokegames.online`、`wss://test.guokegames.online`。

---

## 三、日常发布流程

| 步骤 | 操作 |
|------|------|
| 改代码 | 本地 dev 自测 |
| 上测试 | 打包 test profile → 覆盖 `delta-admin-test.jar` → **只重启测试 Java** → 更新 `dist-test` |
| 上正式 | 验收通过后 → 覆盖正式 jar → **只重启 8080** |

**不要**在测试环境验证时重启正式 8080 项目。
