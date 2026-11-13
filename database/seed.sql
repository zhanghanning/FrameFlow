-- 演示数据只在新建空库执行一次。金额及平台流水均为虚构的教学演示业务。
-- DEMO_PASSWORD 由应用首次启动时使用 BCrypt 编码器替换。
-- 素材为部署脚本生成的 SVG 插画，脚本会回填真实 size_bytes。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO creator(id,name,handle,email,phone,bio) VALUES
(1,'林知夏','linzhixia','zhixia@example.com','13800001001','城市观察与生活方式创作者，用影像记录日常中的微光。'),
(2,'陈屿','chenyu','chenyu@example.com','13800001002','户外摄影与旅行纪录，负责拍摄、航拍及画面调色。'),
(3,'周予安','zhouyuan','yuan@example.com','13800001003','人物访谈导演，擅长品牌故事与纪实短片。'),
(4,'许墨','xumo','xumo@example.com','13800001004','剪辑与动态图形设计师，负责片头包装和音乐节奏。'),
(5,'苏禾','suhe','suhe@example.com','13800001005','美食与器物摄影，关注自然光和食物的细节。'),
(6,'陆遥','luyao','luyao@example.com','13800001006','数码与消费品测评，负责选题策划和产品摄影。');

INSERT INTO app_user(id,username,password_digest,display_name,creator_id) VALUES
(1,'admin','DEMO_PASSWORD','工作室管理员',NULL),
(2,'producer','DEMO_PASSWORD','项目统筹 · 宋宁',NULL),
(3,'finance','DEMO_PASSWORD','财务 · 梁清',NULL),
(4,'reviewer','DEMO_PASSWORD','审核 · 顾远',NULL),
(5,'creator','DEMO_PASSWORD','林知夏',1);
INSERT INTO app_role(id,code,name) VALUES
(1,'ADMIN','管理员'),(2,'PRODUCER','项目统筹'),(3,'FINANCE','财务'),(4,'REVIEWER','审核'),(5,'CREATOR','创作者');
INSERT INTO user_role(user_id,role_id) VALUES(1,1),(2,2),(3,3),(4,4),(5,5);

INSERT INTO platform(id,code,name) VALUES(1,'DOUYIN','抖音'),(2,'BILIBILI','哔哩哔哩'),(3,'XIAOHONGSHU','小红书');
INSERT INTO platform_account(id,platform_id,creator_id,name,external_id) VALUES
(1,1,1,'知夏的城市手记','demo-dy-zhixia'),(2,3,1,'知夏的日常切片','demo-xhs-zhixia'),
(3,2,2,'陈屿在路上','demo-bili-chenyu'),(4,1,3,'予安观察室','demo-dy-yuan'),
(5,3,5,'苏禾的餐桌','demo-xhs-suhe'),(6,2,6,'陆遥好物研究所','demo-bili-luyao'),
(7,2,4,'许墨的剪辑间','demo-bili-xumo'),(8,1,2,'陈屿山海影像','demo-dy-chenyu');

INSERT INTO project(id,name,client_name,description,budget,start_date,due_date,status,owner_id) VALUES
(1,'城市微光 · 生活影像计划','帧序原创','记录城市清晨与下班后的生活片段，形成可持续更新的原创栏目。',36000,'2026-07-01','2026-11-30','PRODUCING',2),
(2,'向山而行 · 秋日徒步季','野径户外','以三条近郊徒步路线为主线，拍摄装备体验及山野纪录短片。',52000,'2026-08-01','2026-11-20','REVIEW',2),
(3,'一杯咖啡的时间','慢岛咖啡','从开店准备到第一位客人，以人物视角讲述社区咖啡馆。',28000,'2026-07-01','2026-09-30','COMPLETED',2),
(4,'看见她 · 人物故事','拾光文化','采访不同职业的女性，制作三集人物纪实短片。',68000,'2026-09-01','2026-12-15','PRODUCING',2),
(5,'海边来信','帧序原创','沿海小城旅行栏目，结合自然风景和城市风物。',30000,'2026-08-01','2026-10-31','COMPLETED',2),
(6,'轻盈生活 · 护肤品牌合作','素研实验室','自然光产品摄影与日常使用场景，交付品牌短视频及图文。',46000,'2026-10-01','2026-11-25','REVIEW',2),
(7,'秋日餐桌提案','青禾餐具','秋季时令食材与手作陶器联合拍摄，形成可复用视觉素材。',24000,'2026-10-01','2026-11-30','PRODUCING',2),
(8,'口袋创作装备指南','简物数码','围绕移动创作场景评测轻量设备，制作横竖屏内容。',42000,'2026-11-01','2026-12-20','PLANNING',2);
INSERT INTO project_member(project_id,creator_id) VALUES
(1,1),(1,4),(2,2),(2,4),(3,1),(3,5),(4,3),(4,4),(5,2),(5,1),(6,1),(6,6),(7,5),(7,4),(8,6),(8,4);

INSERT INTO asset(id,name,kind,creator_id,original_filename,storage_path,mime_type,size_bytes,license_type,license_start,license_end,all_platforms,description,uploaded_by) VALUES
(1,'城市清晨 · 开场视觉','IMAGE',1,'city-dawn.svg','assets/demo/city-dawn.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：暖色晨光与城市天际线。',2),
(2,'山野徒步 · 路线封面','IMAGE',2,'mountain-trail.svg','assets/demo/mountain-trail.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：山脊、步道和层叠森林。',2),
(3,'咖啡馆窗边 · 场景参考','IMAGE',5,'coffee-window.svg','assets/demo/coffee-window.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：窗边的咖啡与午后光影。',2),
(4,'人物访谈 · 视觉模板','IMAGE',3,'studio-portrait.svg','assets/demo/studio-portrait.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：人物访谈的背景与构图参考。',2),
(5,'夜行城市 · 霓虹封面','IMAGE',4,'neon-night.svg','assets/demo/neon-night.svg','image/svg+xml',0,'LICENSED','2026-07-01','2026-11-20',FALSE,'演示授权：仅限抖音与哔哩哔哩，11月20日到期。',2),
(6,'海边日落 · 栏目封面','IMAGE',2,'seaside-sunset.svg','assets/demo/seaside-sunset.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：海岸线与日落。',2),
(7,'轻盈生活 · 产品背景','IMAGE',6,'product-skin.svg','assets/demo/product-skin.svg','image/svg+xml',0,'LICENSED','2026-10-01','2026-12-31',FALSE,'演示品牌授权背景，只用于指定两平台。',2),
(8,'秋日叶片 · 季节视觉','IMAGE',1,'autumn-leaves.svg','assets/demo/autumn-leaves.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：秋日暖色植物。',2),
(9,'地铁与人流 · 动态构图','IMAGE',4,'subway-motion.svg','assets/demo/subway-motion.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：城市通勤与几何线条。',2),
(10,'手作陶器 · 细节视觉','IMAGE',5,'ceramic-detail.svg','assets/demo/ceramic-detail.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：陶器与柔和自然光。',2),
(11,'林间微光 · 外景参考','IMAGE',2,'forest-light.svg','assets/demo/forest-light.svg','image/svg+xml',0,'ORIGINAL','2026-01-01',NULL,TRUE,'项目演示插画：森林中的光束。',2),
(12,'餐桌旧稿 · 授权到期示例','IMAGE',5,'food-table.svg','assets/demo/food-table.svg','image/svg+xml',0,'LICENSED','2026-07-01','2026-10-31',FALSE,'演示过期授权：保留历史素材，发布新作品时需重新授权。',2);

INSERT INTO asset_platform(asset_id,platform_id) VALUES(5,1),(5,2),(7,1),(7,3),(12,3);

INSERT INTO tag(id,name) VALUES(1,'城市'),(2,'生活方式'),(3,'户外'),(4,'品牌合作'),(5,'人物'),(6,'美食'),(7,'自然光'),(8,'封面');
INSERT INTO asset_tag(asset_id,tag_id) VALUES
(1,1),(1,8),(2,3),(2,8),(3,2),(3,7),(4,5),(4,8),(5,1),(5,8),(6,3),(6,7),
(7,4),(7,7),(8,2),(8,7),(9,1),(10,2),(10,7),(11,3),(11,7),(12,6);

INSERT INTO work(id,project_id,title,description,status,created_by) VALUES
(1,1,'城市醒来之前','清晨街道与早餐摊的影像记录。','READY',2),
(2,1,'下班后的两小时','从通勤到独处，寻找城市生活的小出口。','READY',2),
(3,2,'周末去山里走走','近郊徒步路线与轻量装备分享。','READY',2),
(4,2,'山脊上的风','秋日徒步第二集，等待成片审核。','REVIEW',2),
(5,3,'咖啡馆的第一盏灯','店主的一天，从磨豆声开始。','READY',2),
(6,4,'她的第一个工作室','人物访谈第一集，进入精剪阶段。','EDITING',2),
(7,5,'写给海的一封信','沿海小城的日落与生活。','READY',2),
(8,6,'让日常轻一点','品牌短片，产品细节与日常使用。','READY',2),
(9,7,'把秋天端上餐桌','时令食材与陶器的自然光拍摄。','REVIEW',2),
(10,8,'一个背包里的创作工具','轻量创作设备指南，选题策划中。','DRAFT',2),
(11,1,'城市里的秋天','收集街角植物与午后光线。','EDITING',2),
(12,7,'一只陶碗的温度','手作器物与生活方式短片。','EDITING',2);
INSERT INTO work_asset(work_id,asset_id) VALUES
(1,1),(1,9),(2,5),(2,9),(3,2),(3,11),(4,2),(4,11),(5,3),(5,10),(6,4),(7,6),(8,7),(9,8),(9,10),(10,7),(11,8),(12,10);

INSERT INTO project_asset(project_id,asset_id,shared_by)
SELECT w.project_id,wa.asset_id,MIN(w.created_by)
FROM work_asset wa JOIN work w ON w.id=wa.work_id GROUP BY w.project_id,wa.asset_id;

INSERT INTO publication(id,work_id,platform_account_id,external_id,published_at,view_count,created_by) VALUES
(1,1,1,'demo-pub-001','2026-07-15 08:00:00',428600,2),
(2,1,2,'demo-pub-002','2026-07-16 12:00:00',186400,2),
(3,2,1,'demo-pub-003','2026-09-12 18:30:00',562300,2),
(4,3,3,'demo-pub-004','2026-08-20 18:00:00',316800,2),
(5,3,8,'demo-pub-005','2026-08-21 12:00:00',228900,2),
(6,5,2,'demo-pub-006','2026-07-25 10:00:00',298700,2),
(7,5,5,'demo-pub-007','2026-07-26 11:00:00',142500,2),
(8,7,3,'demo-pub-008','2026-09-02 18:00:00',687200,2),
(9,7,8,'demo-pub-009','2026-09-03 12:00:00',354100,2),
(10,8,2,'demo-pub-010','2026-10-20 19:00:00',198600,2);

INSERT INTO contract(id,project_id,name,start_date,end_date,studio_rate,created_by) VALUES
(1,1,'城市微光 · 年度栏目分账协议','2026-07-01','2026-12-31',30,3),
(2,2,'野径户外 · 秋日合作分账协议','2026-08-01','2026-12-31',35,3),
(3,3,'慢岛咖啡 · 内容合作分账协议','2026-07-01','2026-09-30',30,3),
(4,4,'看见她 · 纪实栏目分账协议','2026-09-01','2026-12-31',30,3),
(5,5,'海边来信 · 旅行栏目分账协议','2026-08-01','2026-10-31',25,3),
(6,6,'素研 · 品牌合作分账协议','2026-10-01','2026-12-31',35,3),
(7,7,'青禾 · 秋季拍摄分账协议','2026-10-01','2026-12-31',30,3),
(8,8,'简物 · 数码栏目分账协议','2026-11-01','2026-12-31',30,3);
INSERT INTO contract_participant(contract_id,creator_id,rate) VALUES
(1,1,50),(1,4,20),(2,2,45),(2,4,20),(3,1,45),(3,5,25),(4,3,50),(4,4,20),
(5,2,50),(5,1,25),(6,1,40),(6,6,25),(7,5,50),(7,4,20),(8,6,50),(8,4,20);
UPDATE contract SET status='ACTIVE' WHERE id<=7;

INSERT INTO revenue(id,publication_id,platform_account_id,source_ref,revenue_date,kind,gross_amount,fee_amount,contract_id,status,note,confirmed_by,confirmed_at,created_by) VALUES
(1,1,1,'DY-202607-001','2026-07-31','INCOME',12800,640,1,'CONFIRMED','七月创作激励与合作收入',3,'2026-08-02 10:00:00',3),
(2,2,2,'XHS-202607-001','2026-07-31','INCOME',8600,430,1,'CONFIRMED','七月平台内容合作',3,'2026-08-02 10:05:00',3),
(3,1,1,'DY-202608-001','2026-08-31','INCOME',7400,370,1,'CONFIRMED','八月长尾收益',3,'2026-09-02 10:00:00',3),
(4,3,1,'DY-202609-001','2026-09-30','INCOME',18600,930,1,'CONFIRMED','九月原创栏目合作',3,'2026-10-02 10:00:00',3),
(5,4,3,'BILI-202608-001','2026-08-31','INCOME',22400,1120,2,'CONFIRMED','品牌合作首期款',3,'2026-09-02 11:00:00',3),
(6,5,8,'DY-202609-002','2026-09-30','INCOME',9600,480,2,'CONFIRMED','户外内容推广收入',3,'2026-10-02 11:00:00',3),
(7,6,2,'XHS-202607-002','2026-07-31','INCOME',13200,660,3,'CONFIRMED','咖啡馆内容合作',3,'2026-08-02 11:00:00',3),
(8,7,5,'XHS-202608-002','2026-08-31','INCOME',4800,240,3,'CONFIRMED','图文内容及素材使用',3,'2026-09-02 11:10:00',3),
(9,8,3,'BILI-202609-002','2026-09-30','INCOME',26800,1340,5,'CONFIRMED','旅行栏目品牌合作',3,'2026-10-02 12:00:00',3),
(10,9,8,'DY-202610-001','2026-10-31','INCOME',11200,560,5,'CONFIRMED','旅行栏目十月收入',3,'2026-11-02 10:00:00',3),
(11,10,2,'XHS-202610-003','2026-10-31','INCOME',32000,1600,6,'CONFIRMED','品牌短片交付收入',3,'2026-11-02 11:00:00',3),
(12,3,1,'DY-202610-002','2026-10-31','INCOME',15800,790,1,'CONFIRMED','十月栏目收入',3,'2026-11-02 11:10:00',3),
(13,4,3,'BILI-202610-003','2026-10-31','INCOME',6800,340,NULL,'DRAFT','等待平台账单确认',NULL,NULL,3),
(14,10,2,'XHS-202611-001','2026-11-12','INCOME',8200,410,NULL,'DRAFT','十一月追加内容费用',NULL,NULL,3);
INSERT INTO revenue(id,publication_id,platform_account_id,source_ref,revenue_date,kind,gross_amount,fee_amount,original_revenue_id,contract_id,status,note,confirmed_by,confirmed_at,created_by) VALUES
(15,6,2,'XHS-REFUND-202611-001','2026-11-05','REFUND',1200,0,7,3,'CONFIRMED','合作内容缩减退款，沿用原协议，进入后续结算',3,'2026-11-06 10:00:00',3);

INSERT INTO project_cost(id,project_id,contract_id,category,description,amount,cost_date,status,approved_by,approved_at,created_by) VALUES
(1,1,1,'拍摄','七月城市栏目拍摄交通与场地',1800,'2026-07-20','APPROVED',4,'2026-07-22 10:00:00',2),
(2,1,1,'后期','七月字幕制作与配音',900,'2026-07-28','APPROVED',4,'2026-07-29 10:00:00',2),
(3,1,1,'拍摄','九月栏目拍摄成本',2400,'2026-09-15','APPROVED',4,'2026-09-16 10:00:00',2),
(4,2,2,'差旅','户外路线交通与住宿',4200,'2026-08-18','APPROVED',4,'2026-08-22 10:00:00',2),
(5,2,2,'器材','户外收音设备租赁',1600,'2026-09-10','APPROVED',4,'2026-09-11 10:00:00',2),
(6,3,3,'拍摄','咖啡馆拍摄与布景',1500,'2026-07-24','APPROVED',4,'2026-07-26 10:00:00',2),
(7,5,5,'差旅','沿海拍摄交通住宿',5600,'2026-09-01','APPROVED',4,'2026-09-05 10:00:00',2),
(8,6,6,'道具','护肤产品拍摄布景与道具',2800,'2026-10-18','APPROVED',4,'2026-10-19 10:00:00',2),
(9,7,NULL,'道具','秋日餐桌拍摄食材与鲜花',680,'2026-11-10','DRAFT',NULL,NULL,2),
(10,4,NULL,'后期','人物访谈配乐授权费用',1200,'2026-11-11','DRAFT',NULL,NULL,2);

-- 使用真实存储过程生成历史结算，验证种子金额与生产计算逻辑相同。
CALL sp_generate_settlement(1,'2026-07-01','2026-07-31',3,@july_settlement);
UPDATE settlement SET status='SUBMITTED',submitted_by=3,submitted_at='2026-08-03 10:00:00',last_actor_id=3 WHERE id=@july_settlement;
UPDATE settlement SET status='APPROVED',approved_by=4,approved_at='2026-08-04 10:00:00',last_actor_id=4 WHERE id=@july_settlement;
CALL sp_generate_settlement(3,'2026-07-01','2026-09-30',3,@coffee_settlement);
UPDATE settlement SET status='SUBMITTED',submitted_by=3,submitted_at='2026-10-03 10:00:00',last_actor_id=3 WHERE id=@coffee_settlement;
UPDATE settlement SET status='APPROVED',approved_by=4,approved_at='2026-10-04 10:00:00',last_actor_id=4 WHERE id=@coffee_settlement;
CALL sp_generate_settlement(2,'2026-08-01','2026-09-30',3,@outdoor_settlement);
UPDATE settlement SET status='SUBMITTED',submitted_by=3,submitted_at='2026-10-05 10:00:00',last_actor_id=3 WHERE id=@outdoor_settlement;
CALL sp_generate_settlement(5,'2026-08-01','2026-10-31',3,@seaside_settlement);

INSERT INTO payment(creator_id,amount,paid_at,reference,note,created_by) VALUES
(1,6000,'2026-08-08 14:30:00','DEMO-PAY-20260808-001','七月栏目部分付款登记',3),
(4,2500,'2026-08-08 14:40:00','DEMO-PAY-20260808-002','七月剪辑分账部分付款登记',3),
(5,3000,'2026-10-08 15:00:00','DEMO-PAY-20261008-001','咖啡项目部分付款登记',3);

INSERT INTO audit_log(user_id,action,entity_type,entity_id,detail) VALUES
(1,'INIT_DEMO','SYSTEM',NULL,'初始化帧序工作室演示数据'),
(3,'CONFIRM_REVENUE','REVENUE',15,'确认退款流水：沿用原收入的分账协议'),
(4,'APPROVE_SETTLEMENT','SETTLEMENT',@coffee_settlement,'审核通过咖啡项目结算');
COMMIT;
