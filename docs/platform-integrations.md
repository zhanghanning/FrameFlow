# 平台账号连接与作品同步（默认关闭）

当前阶段经用户确认：暂停第三方平台接入，撤下授权、同步、待申请/待配置卡片及独立平台作品页签。平台账号页默认只展示业务账号档案，作品页保留作品库与发布记录；平台 Logo、账号归属和发布链接继续使用。

## 显式启用可选模块

后端 `PLATFORM_INTEGRATIONS_ENABLED` 与前端 `VITE_PLATFORM_INTEGRATIONS_ENABLED` 均默认 `false`。只有显式设为 `true` 才开放相应接口和界面。代码、现有数据库表及历史记录保留，关闭状态不调用授权/同步接口。

后端开关从启动环境读取；前端开关由 Vite 在启动或构建时读取。本项目 `scripts/dev.sh` 会加载根目录 `.env`，因此将两项写入本地 `.env` 后重启即可；单独运行 Vite 或生产构建时需显式传入前端变量。开关开启后仍需配置实际平台应用权限、凭据和回调地址。

```bash
# 仅在需要继续接入开发时启用，当前日常运行保持默认关闭
PLATFORM_INTEGRATIONS_ENABLED=true bash scripts/test_backend.sh
# 另一个终端执行原有可选模块 HTTP 验证
python3 scripts/verify_platform_integrations.py
```

下面保留实现范围与未来接入步骤。真实账号授权与作品同步须在取得应用权限和凭据后联调。

## 当前六个平台

| 平台 | 当前接入阶段 | 申请与联调条件 |
| --- | --- | --- |
| YouTube | 读取本人作品的官方适配器，待配置 | Google Cloud 项目、YouTube Data API、OAuth Web 客户端、本人列入测试用户；支持 localhost 回调 |
| Instagram | Instagram Login 路线的官方适配器，待配置 | Instagram 专业账号（Business / Creator）、Meta 应用及对应权限、HTTPS 回调 |
| 哔哩哔哩 | 待申请与接口确认 | 取得开放平台应用、账号授权及投稿读取接口资料后启用 |
| 抖音 | 待申请与接口确认 | 网站应用须满足主体资质，配置 HTTPS 回调，并申请本人内容读取权限 |
| 快手 | 待申请与接口确认 | 取得开放平台应用、内容读取权限及回调要求后启用 |
| 小红书 | 待申请与接口确认 | 确认适用的官方开放能力与账号内容读取权限后启用 |

启用模块时，国内四个平台显示待申请状态；具体接口由获批应用的官方资料确定。手工登记平台账号仍可用于现有发布与收入业务，登记本身不授予平台访问权限。

## 模块启用后的使用流程

1. 在“平台账号”选择平台并授权。创作者连接自己的账号；管理员选择归属创作者。
2. 平台回调完成后，首次同步该账号作品。之后可手动点击同步。
3. 在“作品与发布 → 平台作品”按平台、账号和标题筛选。每张卡片显示平台 Logo、账号与作品信息；悬停 800ms 放大封面，点击打开原平台作品。
4. 授权到期或被撤销后重新连接。解除连接会清除本地令牌，已同步作品与已有业务记录保留。

## 数据与权限

platform_account 保存账号业务档案，platform_connection 保存授权连接，oauth_attempt 保存短期一次性授权请求，platform_content 保存平台返回的内容，platform_sync_job 保存同步结果。远端账号和作品标识区分大小写。重复同步按连接与远端作品 ID 更新，不重复插入。

平台内容仅账号所属创作者和管理员可管理。项目内的制作作品、素材授权、发布记录和收益流水保持各自业务流程；平台同步不会自动建立项目、推断版权或生成收入。

令牌仅存于后端并使用本地密钥加密，接口返回连接摘要。账号连接后固定其平台主体和归属，避免在编辑业务档案时将授权转移给其他人。

## 配置 YouTube

在 Google Cloud 创建项目并启用 YouTube Data API v3，配置 OAuth 同意屏幕和测试用户，创建 Web application 客户端。授权回调填写：

`http://localhost:8080/api/platform-integrations/YOUTUBE/callback`

本地 `.env` 填写 `YOUTUBE_CLIENT_ID`、`YOUTUBE_CLIENT_SECRET`、`YOUTUBE_REDIRECT_URI`。使用的权限为 `https://www.googleapis.com/auth/youtube.readonly`；依次读取本人的 channel、uploads playlist 和 videos 元数据。Testing 模式最多 100 个测试用户，授权及刷新令牌会在 7 天后到期，需要再次授权。

## 配置 Instagram

当前实现采用官方仍提供的 Instagram Login 路线，使用 `instagram_business_basic` 读取本人专业账号媒体。它与新的 Unified Login 的应用类型、权限和令牌不同，申请时须选择对应路线。普通个人账号先转换为 Business 或 Creator 专业账号。

在 Meta 应用配置实际 HTTPS 回调，例如 `https://你的域名/api/platform-integrations/INSTAGRAM/callback`，本地 `.env` 填写 `INSTAGRAM_CLIENT_ID`、`INSTAGRAM_CLIENT_SECRET`、`INSTAGRAM_REDIRECT_URI`。通过该域名将回调转发到本项目后端，并确保浏览器最终能返回 `PLATFORM_FRONTEND_URL`。公开供其他账号使用时按平台要求申请审核及高级权限。

## 共用环境项

`.env.example` 列出了全部配置名称。`PLATFORM_TOKEN_KEY` 使用 Base64 编码的 32 字节随机密钥，本机生成命令：

```bash
python3 -c "import base64,secrets; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

生成后保存到本地 `.env`。该密钥用于读取已保存授权，需与数据库一起妥善保存。`PLATFORM_FRONTEND_URL` 默认 `http://localhost:3000`，`PLATFORM_PUBLIC_BASE_URL` 默认 `http://localhost:8080`。配置完成后重启后端。显式启用模块后，凭据缺失的平台显示待配置状态；默认关闭时不显示接入界面。

数据库迁移：`sudo python3 scripts/init_database.py`，会在业务库及独立测试库执行可重复运行的 `database/platform-integration.sql`。源码包不包含本地凭据与平台令牌。

## 官方资料

核对日期：2026-11-13。

- [YouTube 入门](https://developers.google.com/youtube/v3/getting-started)
- [Google OAuth Web server / localhost](https://developers.google.com/identity/protocols/oauth2/web-server)
- [YouTube uploads playlist](https://developers.google.com/youtube/v3/docs/playlistItems/list)
- [Google OAuth 测试限制](https://support.google.com/cloud/answer/15549945?hl=en)
- [Instagram 平台路线](https://developers.facebook.com/documentation/instagram-platform/overview)
- [Instagram Login 入门](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/get-started?locale=en_US)
- [Instagram 审核](https://developers.facebook.com/docs/instagram-platform/app-review/?locale=en_US)
- [Instagram 媒体字段](https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-media/?locale=en_US)
- [抖音开发者入驻](https://developer.open-douyin.com/docs/resource/zh-CN/developer/join/join-into-developer-platform)
- [抖音授权码](https://developer.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/douyin-get-permission-code)
- [哔哩哔哩开放平台](https://openhome.bilibili.com/)
- [快手开放平台](https://open.kuaishou.com/)
- [小红书开放平台](https://open.xiaohongshu.com/)


## 平台标识

前端使用 Simple Icons 16.37.0 的品牌 SVG 路径，依赖及版本写入 package.json/package-lock.json。平台标识用于注明作品来源，卡片同时展示平台名称。
