# 旅行封面素材（2026-10-21）

本地后备照片打包于 `Frontend/app/src/main/res/drawable-nodpi/`，1000×650 JPEG，显示时 centerCrop。MockPhotoCatalog 复用下表固定链接，填入 Mock 响应；前端现在可加载响应的远程图片，不调用图片搜索/API，不向图库发送用户资料、城市输入或行程。

来源：Unsplash 免费图片；使用许可：https://unsplash.com/license 。不得将原图作为素材库出售或复制竞争图片服务。拍摄地点仅作城市氛围参考，不代表酒店、餐厅、价格或预订。

| 城市 / assetKey | 原始图片地址 |
|---|---|
| Kyoto / city_kyoto | https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e |
| Paris / city_paris | https://images.unsplash.com/photo-1502602898657-3e91760cbb34 |
| Tokyo / city_tokyo | https://images.unsplash.com/photo-1540959733332-eab4deabeeaf |
| Bali / city_bali | https://images.unsplash.com/photo-1537996194471-e657df975ab4 |
| New York / city_new_york | https://images.unsplash.com/photo-1485871981521-5b1fd3805eee |
| Canberra / city_canberra | https://images.unsplash.com/photo-1672264597620-d792bb6de88d |

Canberra 图片页面：https://unsplash.com/photos/a-large-building-with-a-flag-on-top-of-it-WWVLdFbTiVk ，作者 Marcus Reubenstein，页面标注 Parliament House, Canberra。

## 与后端协议的边界

- 既有 `summary.coverImageId` → `media[]` 引用不变；仅加载 READY、未到期、类型及 URL 合法的媒体。列表优先 thumbnailUrl，详情使用 url；旧 assetKey 目录仍可作为本地后备。
- 中央白名单配置为 `Frontend/app/src/main/java/com/example/odyssey/ImageUrlPolicy.java`：仅 HTTPS、images.unsplash.com 与 images.pexels.com，无凭据，每一跳重定向均重新验证。增加提供商须在此审核配置。
- 先显示本地城市照片或中性占位；下载、离线、过期或解码失败保留后备。下载最多 4MB、采样解码最长边 1200、位图缓存 8MB、两个线程及 12 个排队请求；重新绑定和离屏取消阻止旧图覆盖。
- 成功显示响应图片后，在附近显示其 attribution 纯文本，不自动打开 attribution URL；不执行文件路径、HTML，也不用 getIdentifier 猜资源名。
- 城市氛围图不代表具体酒店或餐厅实拍。此实现仍无真实业务后端访问；Mock 与本地示例通过同一媒体协议展示固定目录链接。
