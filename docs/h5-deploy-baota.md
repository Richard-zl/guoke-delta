# 用户端 H5 支付页 — 打包与宝塔部署

## 你需要做的（3 步）

### 1. 本地打包

```bash
cd delta-mp
npm install          # 首次或依赖变更时
npm run build:h5
```

产物目录：

```
delta-mp/dist/build/h5/
```

把该目录下的**全部文件**（含 `index.html`、`assets/`）上传即可。  
**注意：必须连同 `assets/` 整目录一起覆盖**，只传 `index.html` 会导致 JS/CSS 404、页面空白。

### 2. 宝塔上传目录

服务器目录（没有就新建）：

```
/www/wwwroot/mp-h5
```

用宝塔「文件」把 `dist/build/h5/` 里的内容上传到 `/www/wwwroot/mp-h5/`（不要盖掉管理后台的 `/www/wwwroot/dist`）。

上传后服务器上应类似：

```text
/www/wwwroot/mp-h5/index.html
/www/wwwroot/mp-h5/assets/index-xxxx.js
/www/wwwroot/mp-h5/assets/index-xxxx.css
...
```

### 3. 修改 Nginx（主站 443 的 server 内）

在现有 `location / { ... }` **上面**增加（`^~` 很重要，避免被后台静态 `js|css` 正则抢走）：

```nginx
# 用户端 H5（uni-app），与管理后台 dist 分离
# 必须用 ^~，否则常见 location ~* \.(js|css)$ 会把 /h5/assets/* 指到后台目录 → 404 白屏
location ^~ /h5/ {
    alias /www/wwwroot/mp-h5/;
    index index.html;
    try_files $uri $uri/ /h5/index.html;
}
```

若 `alias` + `try_files` 在你这台 Nginx 上异常，可改用：

```nginx
location ^~ /h5/ {
    alias /www/wwwroot/mp-h5/;
    index index.html;
}
```

（hash 路由不依赖服务端 fallback，一般够用。）

重载 Nginx。验证（**三条都要 200**）：

```bash
curl -o /dev/null -w "%{http_code}\n" https://guokegames.online/h5/
curl -o /dev/null -w "%{http_code}\n" https://guokegames.online/h5/index.html
# 把下面文件名换成 index.html 里实际引用的 assets 名
curl -o /dev/null -w "%{http_code}\n" https://guokegames.online/h5/assets/index-xxxxx.js
```

- `https://guokegames.online/` → 仍是管理后台  
- `https://guokegames.online/h5/` → 用户端 H5  
- `https://guokegames.online/h5/#/pages/order/h5pay` → 支付页（需在微信内打开）

若 `index.html` 是 200、但 `/h5/assets/*.js` 是 **404** → 页面一定白屏：检查是否上传了 `assets/`，以及 Nginx 是否加了 `^~`。

## 已配好的仓库项（无需你再改代码）

| 项 | 值 |
|----|-----|
| H5 路由 base | `/h5/`（`manifest.json`） |
| 生产 API | `https://guokegames.online/api`（`.env.production`） |
| yml 支付链接 | `pay.kf.h5-pay-base-url: https://guokegames.online/h5/#/pages/order/h5pay` |

## 服务号后台（部署后做一次）

域名填 `guokegames.online`（不要带 `/h5`）：

- 网页授权域名
- JS 接口安全域名

授权回调实际落到 `https://guokegames.online/h5/`（由前端再跳进支付页）。
