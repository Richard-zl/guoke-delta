/**
 * 审核期静态资讯正文（配图引用三角洲行动官网 CDN）
 */

import { DF_IMG } from './auditImages'

/** 按栏目分组的文章列表 */
export const AUDIT_ARTICLES = {
  guide: [
    {
      id: 'g1',
      categoryId: 'guide',
      title: '三角洲行动 · 新手入门总览',
      summary: '从模式选择、基础操作到首次撤离，一文带你快速上手。',
      author: '游戏百科',
      publishDate: '2025-04-16',
      tags: ['三角洲行动', '新手指南'],
      coverImage: DF_IMG.modeFenghuoMain,
      blocks: [
        { type: 'text', content: '《三角洲行动》是一款新一代战术射击游戏，包含烽火地带、全面战场等多种玩法。本文面向刚接触游戏的玩家，帮助你建立基础认知并顺利完成前几局体验。' },
        { type: 'heading', content: '一、选择适合的模式' },
        { type: 'text', content: '烽火地带强调搜索、对抗与安全撤离，适合喜欢策略与资源管理的玩家；全面战场则是大规模多人对战，更侧重载具协同与据点争夺。新手建议先从烽火地带熟悉枪械手感与地图结构。' },
        { type: 'image', caption: '烽火地带：搜索物资后安全撤离', url: DF_IMG.modeFenghuoBg },
        { type: 'heading', content: '二、基础操作要点' },
        { type: 'text', content: '完成新手教程后，建议在训练场练习瞄准、换弹与掩体利用。注意保持小地图观察习惯，听到脚步声或枪声时先找掩体再判断方位。' },
        { type: 'tip', content: '首次入局优先携带基础医疗与弹药，不要过度堆叠高价值物资，熟悉流程比「赚币」更重要。' }
      ]
    },
    {
      id: 'g2',
      categoryId: 'guide',
      title: '烽火地带玩法详解',
      summary: '装备选择、曼德尔砖争夺与安全撤离机制说明。',
      author: '游戏百科',
      publishDate: '2025-03-28',
      tags: ['烽火地带', '玩法'],
      coverImage: DF_IMG.modeFenghuo,
      blocks: [
        { type: 'text', content: '烽火地带是《三角洲行动》的核心 PvPvE 模式。玩家需要选好装备进入战局，搜索情报与物资，击败对手并与队友协作，最终从撤离点带出收益。' },
        { type: 'heading', content: '兵种搭配' },
        { type: 'text', content: '可选择不同兵种组成 3 人小队，各兵种拥有独特战术道具。突击位负责突破，支援位提供火力或治疗，信息位负责侦察与标记，合理分工能显著提升胜率。' },
        { type: 'heading', content: '曼德尔砖机制' },
        { type: 'text', content: '曼德尔砖是高价值争夺目标，破译成功有机会获得稀有奖励。携带曼德尔砖时位置会向所有小队暴露，需要团队掩护与路线规划。' },
        { type: 'image', caption: '团队协作破译高价值目标', url: DF_IMG.modeFenghuoMain },
        { type: 'heading', content: '安全撤离' },
        { type: 'text', content: '活着到达撤离点才能保留物资。部分地图存在特殊撤离方式，建议开局前与队友沟通备选路线。' }
      ]
    },
    {
      id: 'g3',
      categoryId: 'guide',
      title: '全面战场模式指南',
      summary: '超大地图、载具作战与据点攻防的入门思路。',
      author: '游戏百科',
      publishDate: '2025-03-15',
      tags: ['全面战场', '载具'],
      coverImage: DF_IMG.modeQuanmianMain,
      blocks: [
        { type: 'text', content: '全面战场传承经典《三角洲特种部队》体验，在超大地图上进行多人作战。相比烽火地带，本模式更强调宏观局势与载具运用。' },
        { type: 'heading', content: '经典地图与还原体验' },
        { type: 'text', content: '地图布局复刻经典场景，老玩家可快速建立空间感。新玩家建议先跟随小队行动，观察重生点与前线变化。' },
        { type: 'heading', content: '载具作战' },
        { type: 'text', content: '可驾驶攻击艇、主战坦克、直升机等多种载具。驾驶员需与步兵沟通推进节奏，避免孤军深入被集火。' },
        { type: 'image', caption: '海陆空多维度协同作战', url: DF_IMG.modeQuanmianBg },
        { type: 'tip', content: '步兵注意反载具道具的携带时机，载具被毁时会形成新的突破口。' }
      ]
    },
    {
      id: 'g4',
      categoryId: 'guide',
      title: '干员选择与团队搭配',
      summary: '四大兵种定位、道具协同与常见阵容推荐。',
      author: '游戏百科',
      publishDate: '2025-02-20',
      tags: ['干员', '团队'],
      coverImage: DF_IMG.modeTiaozhan,
      blocks: [
        { type: 'text', content: '干员（兵种）决定你在小队中的职责。没有「绝对最强」的干员，只有与战术匹配的阵容。' },
        { type: 'heading', content: '常见阵容思路' },
        { type: 'text', content: '标准三人组可采用「突破 + 信息 + 支援」结构：一人开路，一人报点与控场，一人负责治疗或重火力压制。' },
        { type: 'heading', content: '道具交换时机' },
        { type: 'text', content: '进点前统一投掷顺序：烟雾封视线 → 闪光逼走位 → 突击进入。避免多人同时浪费同类道具。' },
        { type: 'image', caption: '进点前统一战术沟通', url: DF_IMG.modeQuanmian }
      ]
    },
    {
      id: 'g5',
      categoryId: 'guide',
      title: '枪械改装基础入门',
      summary: '后坐力、射程与操控性的平衡思路。',
      author: '游戏百科',
      publishDate: '2025-02-08',
      tags: ['枪械', '改装'],
      coverImage: DF_IMG.modeHeiying,
      blocks: [
        { type: 'text', content: '改装并非堆满配件就好。需要根据地图距离、个人压枪能力与作战角色选择握把、枪管与瞄具。' },
        { type: 'heading', content: '近距离 vs 远距离' },
        { type: 'text', content: 'CQB 场景优先提升操控与腰射稳定；中远距离则可适当牺牲机动性换取精准度。' },
        { type: 'image', caption: '根据作战距离选择改装方案', url: DF_IMG.modeHongshu },
        { type: 'heading', content: '弹药类型' },
        { type: 'text', content: '不同弹药穿透与伤害各异，进入战局前确认弹种与备用弹药数量，避免交火中无法补充。' },
        { type: 'tip', content: '在训练场对比同一枪械的两套改装，选「自己能控住」的方案比抄作业更有效。' }
      ]
    },
    {
      id: 'g6',
      categoryId: 'guide',
      title: '地图资源点与撤离路线规划',
      summary: '如何读图、避战与选择撤离窗口。',
      author: '游戏百科',
      publishDate: '2025-01-22',
      tags: ['地图', '战术'],
      coverImage: DF_IMG.modeShengzhe,
      blocks: [
        { type: 'text', content: '熟悉资源点分布与撤离点位置，是烽火地带稳定收益的关键。' },
        { type: 'heading', content: '开局路线' },
        { type: 'text', content: '避免所有队伍争抢同一热点。可选择次要资源区发育，待首轮交火后再转移。' },
        { type: 'heading', content: '听声辨位与避战' },
        { type: 'text', content: '并非每次遭遇都需要交战。任务导向或保全物资时，绕开强敌同样是正确决策。' },
        { type: 'image', caption: '规划主撤离与备用撤离路线', url: DF_IMG.modeFenghuoBg }
      ]
    }
  ],
  newbie: [
    {
      id: 'n1',
      categoryId: 'newbie',
      title: '第一次进游戏该做什么',
      summary: '设置、键位、灵敏度与基础练习建议。',
      author: '新手指南',
      publishDate: '2025-04-10',
      tags: ['入门', '设置'],
      coverImage: DF_IMG.modeFenghuoBg,
      blocks: [
        { type: 'text', content: '首次登录建议按以下顺序完成配置，可显著降低前几局的挫败感。' },
        { type: 'heading', content: '1. 完成教程与键位检查' },
        { type: 'text', content: '确认蹲伏、瞄准、交互、医疗等按键顺手。移动端玩家可适当放大交互按钮区域。' },
        { type: 'image', caption: '熟悉烽火地带基础流程', url: DF_IMG.modeFenghuo },
        { type: 'heading', content: '2. 灵敏度微调' },
        { type: 'text', content: '从偏低灵敏度开始，在训练场逐步上调至能稳定跟枪。不同干员技能释放可能需要单独适应。' },
        { type: 'heading', content: '3. 首局目标' },
        { type: 'text', content: '第一局以「熟悉撤离流程」为目标，不必强求高价值物资。' },
        { type: 'tip', content: '与一位有经验的朋友组队，比单人摸索效率更高。' }
      ]
    },
    {
      id: 'n2',
      categoryId: 'newbie',
      title: '如何提升沟通效率',
      summary: '报点、报血、报道具等实用沟通技巧。',
      author: '新手指南',
      publishDate: '2025-03-30',
      tags: ['沟通', '团队'],
      coverImage: DF_IMG.modeQuanmian,
      blocks: [
        { type: 'text', content: '有效沟通是战术射击游戏的底座。信息越短、越准，队友反应越快。' },
        { type: 'heading', content: '标准报点格式' },
        { type: 'text', content: '推荐「方位 + 距离 + 数量 + 动作」，例如：「东北 50 米，两人，进点」。避免冗长描述。' },
        { type: 'heading', content: '状态同步' },
        { type: 'text', content: '及时告知自己的血量、弹药与大招/道具冷却。残血时主动后撤并请求掩护。' },
        { type: 'image', caption: '团队协同是取胜关键', url: DF_IMG.modeQuanmianMain }
      ]
    },
    {
      id: 'n3',
      categoryId: 'newbie',
      title: '灵敏度与键位设置建议',
      summary: '找到适合自己手型的操控方案。',
      author: '新手指南',
      publishDate: '2025-03-12',
      tags: ['设置', '操控'],
      coverImage: DF_IMG.slogan,
      blocks: [
        { type: 'text', content: '没有 universal 灵敏度，只有适合你的方案。建议分「腰射」「开镜」「陀螺仪」三步单独调试。' },
        { type: 'heading', content: '键位布局' },
        { type: 'text', content: '拇指活动区域优先放置高频操作：开火、瞄准、蹲伏。低频技能可略小或靠上。' },
        { type: 'image', caption: '三角洲行动', url: DF_IMG.logo },
        { type: 'tip', content: '每次只改一个参数并打一局验证，避免一次改太多无法判断效果。' }
      ]
    },
    {
      id: 'n4',
      categoryId: 'newbie',
      title: '训练场练习路线',
      summary: '30 分钟高效练习清单。',
      author: '新手指南',
      publishDate: '2025-02-25',
      tags: ['练习', '枪法'],
      coverImage: DF_IMG.modeTiaozhan,
      blocks: [
        { type: 'text', content: '训练场适合碎片化提升。以下路线适合新手每日热身。' },
        { type: 'heading', content: '10 分钟压枪' },
        { type: 'text', content: '选中常用手感枪械，中距离扫射转移目标，观察弹道集中区域。' },
        { type: 'image', caption: '烽火挑战：激斗争锋', url: DF_IMG.modeTiaozhan },
        { type: 'heading', content: '10 分钟身法' },
        { type: 'text', content: '练习急停、peek 与掩体进出节奏，培养「开枪前先定位」的习惯。' },
        { type: 'heading', content: '10 分钟道具' },
        { type: 'text', content: '反复练习烟雾与闪光投掷点位，记住常用进点烟雾轨迹。' }
      ]
    },
    {
      id: 'n5',
      categoryId: 'newbie',
      title: '仓库与物资管理入门',
      summary: '整理背包、了解物品价值与入局配装思路。',
      author: '新手指南',
      publishDate: '2025-02-10',
      tags: ['仓库', '配装'],
      coverImage: DF_IMG.modeHeiying,
      blocks: [
        { type: 'text', content: '仓库是烽火地带的经济中枢。合理管理物资能避免「有货用不出」或「空手入局」。' },
        { type: 'heading', content: '物品分类' },
        { type: 'text', content: '建议按「武器/护甲/医疗/任务/交易品」分区整理，出售前确认是否为任务所需。' },
        { type: 'image', caption: '黑鹰坠落：高难度协作', url: DF_IMG.modeHeiying },
        { type: 'heading', content: '配装原则' },
        { type: 'text', content: '入局装备应与目标匹配：熟悉地图时可适度投入；实验新路线时使用低成本配装。' }
      ]
    }
  ],
  news: [
    {
      id: 'w1',
      categoryId: 'news',
      title: '回声赛季正式上线公告',
      summary: '全新赛季玩法调整、活动概览与版本亮点一览。',
      author: '官方资讯',
      publishDate: '2025-04-16',
      tags: ['赛季', '更新'],
      coverImage: DF_IMG.slogan,
      blocks: [
        { type: 'text', content: '《三角洲行动》回声赛季已正式上线。本次更新带来全新赛季任务、平衡性调整与多项社区活动。' },
        { type: 'heading', content: '赛季亮点' },
        { type: 'text', content: '烽火地带新增赛季挑战目标；全面战场优化载具手感与部分地图光照表现；多项干员技能微调旨在提升团队配合空间。' },
        { type: 'image', caption: '回声赛季', url: DF_IMG.slogan },
        { type: 'heading', content: '活动预告' },
        { type: 'text', content: '赛季期间将开放登录奖励、挑战任务与社区观赛活动。详情请关注平台公告与官方渠道。' }
      ]
    },
    {
      id: 'w2',
      categoryId: 'news',
      title: '烽火职业联赛观赛指南',
      summary: '赛程安排、观赛渠道与新手看懂比赛的要领。',
      author: '官方资讯',
      publishDate: '2025-04-02',
      tags: ['赛事', '观赛'],
      coverImage: DF_IMG.modeShengzhe,
      blocks: [
        { type: 'text', content: '烽火职业联赛汇聚顶尖战队，是学习高阶战术的绝佳途径。' },
        { type: 'heading', content: '如何观赛' },
        { type: 'text', content: '关注官方赛事直播与回放频道。建议先看地图 Ban/Pick 与开局部署，再看中期转点与道具交换。' },
        { type: 'image', caption: '胜者为王：指挥官模式', url: DF_IMG.modeShengzhe },
        { type: 'tip', content: '暂停时思考「若是我会如何处理这个局面」，比单纯看击杀集锦收获更大。' }
      ]
    },
    {
      id: 'w3',
      categoryId: 'news',
      title: '4 月平衡性调整说明',
      summary: '部分枪械、干员技能与地图机制的改动摘要。',
      author: '官方资讯',
      publishDate: '2025-03-25',
      tags: ['平衡', '更新'],
      coverImage: DF_IMG.logo,
      blocks: [
        { type: 'text', content: '开发团队持续根据数据与社区反馈进行平衡性迭代。以下为本次调整要点摘要。' },
        { type: 'heading', content: '枪械调整' },
        { type: 'text', content: '部分冲锋枪中距离衰减优化；若干狙击步枪开镜速度微调，旨在丰富武器选用场景。' },
        { type: 'image', caption: '三角洲行动', url: DF_IMG.modeHongshu },
        { type: 'heading', content: '干员技能' },
        { type: 'text', content: '信息类技能冷却与范围小幅调整，鼓励更频繁的团队沟通而非单人 carry。' }
      ]
    },
    {
      id: 'w4',
      categoryId: 'news',
      title: '高校烽火杯报名开启',
      summary: '校园赛规则、报名流程与赛程节点说明。',
      author: '官方资讯',
      publishDate: '2025-03-08',
      tags: ['高校赛', '报名'],
      coverImage: DF_IMG.modeFenghuoMain,
      blocks: [
        { type: 'text', content: '高校烽火杯面向在校学生战队开放报名，旨在发掘年轻选手并促进校园电竞交流。' },
        { type: 'heading', content: '报名条件' },
        { type: 'text', content: '需以学校为单位组队，提交战队信息与队员学籍验证材料。具体规则以赛事官网为准。' },
        { type: 'image', caption: '烽火地带竞技', url: DF_IMG.modeFenghuoMain },
        { type: 'heading', content: '赛程概览' },
        { type: 'text', content: '分为校际海选、区域赛与总决赛阶段。获胜队伍将获得荣誉奖励与官方曝光机会。' }
      ]
    }
  ],
  club: [
    {
      id: 'c1',
      categoryId: 'club',
      title: '关于我们',
      summary: '俱乐部理念、社区文化与成员服务介绍。',
      author: '俱乐部',
      publishDate: '2025-04-01',
      tags: ['俱乐部', '介绍'],
      coverImage: DF_IMG.modeQuanmianMain,
      blocks: [
        { type: 'text', content: '我们致力于搭建健康、积极的电竞社区，为玩家提供资讯交流、活动参与与客服支持。' },
        { type: 'heading', content: '社区价值观' },
        { type: 'text', content: '尊重、公平、成长——尊重每一位成员，公平对待每一场对局，在交流与活动中共同成长。' },
        { type: 'heading', content: '我们提供' },
        { type: 'text', content: '游戏攻略与资讯、线下观赛与交流活动、会员等级与积分福利、专属客服通道。' },
        { type: 'image', caption: '全面战场团队作战', url: DF_IMG.modeQuanmianBg }
      ]
    },
    {
      id: 'c2',
      categoryId: 'club',
      title: '社区活动预告',
      summary: '线下聚会、观赛活动与会员福利说明。',
      author: '俱乐部',
      publishDate: '2025-03-20',
      tags: ['活动', '预告'],
      coverImage: DF_IMG.modeShengzhe,
      blocks: [
        { type: 'text', content: '俱乐部不定期举办观赛活动、新手交流局与主题沙龙。以下为近期计划概览。' },
        { type: 'heading', content: '五月观赛夜' },
        { type: 'text', content: '组织成员共同观看烽火联赛关键场次，赛后由资深玩家复盘战术要点。' },
        { type: 'image', caption: '赛事观赛活动', url: DF_IMG.slogan },
        { type: 'heading', content: '新手交流局' },
        { type: 'text', content: '面向新成员的组队练习活动，老会员带队讲解基础报点与配装思路。' }
      ]
    },
    {
      id: 'c3',
      categoryId: 'club',
      title: '会员等级与积分说明',
      summary: '等级名称、积分获取方式与权益介绍。',
      author: '俱乐部',
      publishDate: '2025-03-05',
      tags: ['会员', '积分'],
      coverImage: DF_IMG.logo,
      blocks: [
        { type: 'text', content: '会员等级反映你在社区的活跃度与贡献。积分可通过签到、参与活动、完成社区任务等方式获取。' },
        { type: 'heading', content: '等级权益' },
        { type: 'text', content: '高等级会员可优先报名线下活动、获得专属标识与节日福利。具体权益以平台公告为准。' },
        { type: 'tip', content: '积分不可转让，请勿相信任何「代刷积分」信息。' }
      ]
    },
    {
      id: 'c4',
      categoryId: 'club',
      title: '俱乐部观赛活动回顾',
      summary: '三月观赛夜精彩瞬间与成员心得分享。',
      author: '俱乐部',
      publishDate: '2025-02-28',
      tags: ['回顾', '观赛'],
      coverImage: DF_IMG.modeQuanmianMain,
      blocks: [
        { type: 'text', content: '三月观赛夜共有 40 余名成员参与，共同观看了联赛半决赛关键局。' },
        { type: 'heading', content: '复盘要点' },
        { type: 'text', content: '重点讨论了 B 点道具交换顺序与最后 30 秒转点决策。成员反馈「理解了职业队为何敢卖信息」。' },
        { type: 'image', caption: '联赛精彩瞬间', url: DF_IMG.modeQuanmian }
      ]
    }
  ],
  help: [
    {
      id: 'h1',
      categoryId: 'help',
      title: '账号与安全',
      summary: '密码保护、异常登录处理与防诈骗提醒。',
      author: '帮助中心',
      publishDate: '2025-04-05',
      tags: ['安全', '账号'],
      coverImage: DF_IMG.logo,
      blocks: [
        { type: 'text', content: '保护账号安全是享受游戏与社区服务的前提。请仔细阅读以下建议。' },
        { type: 'heading', content: '密码与验证码' },
        { type: 'text', content: '请勿向任何人透露账号密码、短信验证码或支付密码。官方客服不会索要此类信息。' },
        { type: 'heading', content: '异常登录' },
        { type: 'text', content: '收到陌生设备登录提醒时，请立即修改密码并检查绑定手机号。' },
        { type: 'tip', content: '警惕「低价代练」「内部渠道」等诈骗话术，谨防财产损失。' }
      ]
    },
    {
      id: 'h2',
      categoryId: 'help',
      title: '常见问题',
      summary: '登录、消息通知、隐私设置等使用说明。',
      author: '帮助中心',
      publishDate: '2025-03-18',
      tags: ['FAQ', '使用'],
      coverImage: DF_IMG.slogan,
      blocks: [
        { type: 'heading', content: '无法登录怎么办？' },
        { type: 'text', content: '请检查网络连接，尝试重新授权微信登录。若仍失败，可清除小程序缓存后重试。' },
        { type: 'image', caption: '三角洲行动', url: DF_IMG.logo },
        { type: 'heading', content: '收不到消息通知？' },
        { type: 'text', content: '请在系统设置中开启微信与小程序通知权限，并在小程序内确认消息订阅状态。' },
        { type: 'heading', content: '如何修改昵称头像？' },
        { type: 'text', content: '进入「我的 → 编辑资料」即可修改。昵称需符合社区规范。' }
      ]
    },
    {
      id: 'h3',
      categoryId: 'help',
      title: '隐私与数据说明',
      summary: '我们收集哪些信息、用于何种目的。',
      author: '帮助中心',
      publishDate: '2025-03-01',
      tags: ['隐私', '数据'],
      coverImage: DF_IMG.modeFenghuo,
      blocks: [
        { type: 'text', content: '我们重视用户隐私，仅收集提供服务所必需的信息。' },
        { type: 'heading', content: '信息类型' },
        { type: 'text', content: '包括微信授权的基础资料、手机号（经您授权）、设备信息与操作日志，用于账号识别、消息推送与服务改进。' },
        { type: 'heading', content: '您的权利' },
        { type: 'text', content: '您可查阅、更正个人信息，或申请注销账号。详见《隐私政策》。' }
      ]
    },
    {
      id: 'h4',
      categoryId: 'help',
      title: '联系客服指南',
      summary: '客服入口、服务时间与问题反馈建议。',
      author: '帮助中心',
      publishDate: '2025-02-15',
      tags: ['客服', '反馈'],
      coverImage: DF_IMG.logo,
      blocks: [
        { type: 'text', content: '遇到问题可通过小程序内客服入口联系我们。' },
        { type: 'heading', content: '如何联系' },
        { type: 'text', content: '路径：「我的 → 客服」或首页右下角客服按钮。建议附上问题截图与发生时间，便于快速定位。' },
        { type: 'image', caption: '官方品牌标识', url: DF_IMG.logo },
        { type: 'heading', content: '服务时间' },
        { type: 'text', content: '人工客服工作时间为每日 10:00–22:00，非工作时间可留言，我们会尽快回复。' }
      ]
    }
  ]
}
