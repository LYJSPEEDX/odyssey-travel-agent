# 旅行封面素材（2026-10-21）

本轮为离线原型素材，不调用图片搜索/API，不传输用户行程。照片打包于 `Frontend/app/src/main/res/drawable-nodpi/`，1000×650 JPEG，显示时 centerCrop。

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

- 既有 `summary.coverImageId` → `media[]` 引用不变；`status=READY` 且 `assetKey` 命中上表时显示本地素材。
- 本次未接网络 `url` / `thumbnailUrl`；未知 key、缺图和不可用媒体使用匹配城市的离线氛围图或明确占位。
- 不执行模型返回的文件路径、HTML、URL，不用 `getIdentifier` 猜测资源名。
- 城市氛围图不能放到具体餐厅/酒店卡上声称是该场所实拍。未来远程图库需要独立处理来源、作者署名、缓存、到期与许可条款。
