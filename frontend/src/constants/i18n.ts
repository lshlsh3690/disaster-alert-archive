//i18n은 internationalization 의 약자

export const i18n = {
    ko: {
        // 공통
        appName: "재난 안전문자 아카이브",
        loading: "불러오는 중...",

        // 헤더 네비게이션
        nav: {
            dashboard: "대시보드",
            alerts: "재난 문자",
            community: "커뮤니티",
            login: "로그인",
            logout: "로그아웃",
            settings: "설정",
            user: "사용자",
        },

        // 홈
        home: {
            title: "재난 안전문자 아카이브",
            subtitle: "이 플랫폼은 과거 재난문자를 누구나 쉽게 확인하고,\n그에 대한 의견을 나눌 수 있도록 만들었습니다.",
            ctaTitle: "지금 대시보드를 확인해보세요",
            ctaButton: "재난 문자 확인하러 가기",
            features: {
                latest: { title: "최신 재난 문자", desc: "전국에서 수신된 재난 문자를 지역별로 확인" },
                missing: { title: "실종자 정보", desc: "경찰청 공개 실종자 데이터를 통합 조회" },
                stats: { title: "통계 및 그래프", desc: "일별/지역별 재난 알림 통계를 시각적으로 확인" },
                community: { title: "커뮤니티 제보", desc: "지역별 피해 제보와 실시간 댓글 공유" },
            },
        },

        // 재난 문자 목록
        alertList: {
            title: "🗂 재난 문자 아카이브",
            description: "과거 수신된 모든 재난 문자 목록입니다. 지역/날짜/키워드로 검색할 수 있어요.",
            report: "제보 등록하기",
            search: "검색",
            searching: "검색 중...",
            reset: "초기화",
            prev: "이전",
            next: "다음",
            filter: {
                sido: "시/도(전체)",
                sigungu: "시/군/구(전체)",
                type: "유형(전체)",
                level: "레벨(전체)",
                sourceAll: "출처(전체)",
                sourceOfficial: "공공 알림만",
                sourceUser: "사용자 제보만",
                keyword: "키워드(예: 경보)",
                startDate: "시작일",
                endDate: "종료일",
            },
        },

        // 재난 문자 상세
        alertDetail: {
            original: "원문",
            translated: "번역",
            disasterType: "재난 유형",
            region: "지역",
            occurredAt: "발령 시각",
            comments: "댓글",
        },

        // 커뮤니티
        community: {
            title: "커뮤니티",
        },

        // 설정
        settings: {
            title: "설정",
            language: "언어 설정",
        },


        // 푸터
        footer: "© 2025 재난 안전문자 플랫폼 | 문의: lshlsh3690@gmail.com",


        metros: {
            "서울특별시": "서울특별시", "부산광역시": "부산광역시", "대구광역시": "대구광역시",
            "인천광역시": "인천광역시", "광주광역시": "광주광역시", "대전광역시": "대전광역시",
            "울산광역시": "울산광역시", "세종특별자치시": "세종특별자치시", "경기도": "경기도",
            "강원특별자치도": "강원특별자치도", "충청북도": "충청북도", "충청남도": "충청남도",
            "전북특별자치도": "전북특별자치도", "전라남도": "전라남도", "경상북도": "경상북도",
            "경상남도": "경상남도", "제주특별자치도": "제주특별자치도",
        },

        disasterTypes: {
            "폭염": "폭염", "기타": "기타", "호우": "호우", "산불": "산불", "대설": "대설",
            "한파": "한파", "강풍": "강풍", "화재": "화재", "산사태": "산사태", "홍수": "홍수",
            "가축질병": "가축질병", "교통사고": "교통사고", "건조": "건조", "수도": "수도", "풍랑": "풍랑",
            "교통통제": "교통통제", "전염병": "전염병", "미세먼지": "미세먼지", "지진": "지진", "정전": "정전",
            "환경오염사고": "환경오염사고", "붕괴": "붕괴", "교통": "교통", "태풍": "태풍", "안개": "안개",
            "황사": "황사", "통신": "통신", "민방공": "민방공", "지진해일": "지진해일", "가뭄": "가뭄",
            "테러": "테러", "금융": "금융", "에너지": "에너지", "폭발": "폭발", "AI": "AI",
        },
        levels: { "안전안내": "안전안내", "긴급재난": "긴급재난", "위급재난": "위급재난" },
        dashboard: {
            todayAlerts: "오늘 재난문자",
            todayUserReports: "오늘 사용자 제보",
            totalUserReports: "누적 사용자 제보 수",
            latestAlerts: "📩 최신 재난 문자",
            alertMap: "🗺️ 지역별 재난 문자 지도",
            latestComments: "💬 최신 댓글",
            viewAll: "전체 보기 →",
            noComments: "최근 댓글이 없습니다.",
            anonymous: "익명",
            langLabel: "언어",
            count: "건"
        },
    },

    en: {
        appName: "Disaster Alert Archive",
        loading: "Loading...",

        nav: {
            dashboard: "Dashboard",
            alerts: "Alerts",
            community: "Community",
            login: "Login",
            logout: "Logout",
            settings: "Settings",
            user: "User",
        },

        home: {
            title: "Disaster Alert Archive",
            subtitle: "A platform where anyone can easily browse past disaster alerts\nand share their thoughts.",
            ctaTitle: "Check the dashboard now",
            ctaButton: "View Disaster Alerts",
            features: {
                latest: { title: "Latest Alerts", desc: "View disaster alerts by region from across the country" },
                missing: { title: "Missing Persons", desc: "Integrated search of public missing persons data" },
                stats: { title: "Statistics & Charts", desc: "Visual statistics of daily and regional disaster alerts" },
                community: { title: "Community Reports", desc: "Share regional damage reports and real-time comments" },
            },
        },

        alertList: {
            title: "🗂 Disaster Alert Archive",
            description: "All past disaster alerts. Search by region, date, or keyword.",
            report: "Submit a Report",
            search: "Search",
            searching: "Searching...",
            reset: "Reset",
            prev: "Prev",
            next: "Next",
            filter: {
                sido: "Province (All)",
                sigungu: "City/District (All)",
                type: "Type (All)",
                level: "Level (All)",
                sourceAll: "Source (All)",
                sourceOfficial: "Official Only",
                sourceUser: "User Reports Only",
                keyword: "Keyword (e.g. warning)",
                startDate: "Start Date",
                endDate: "End Date",
            },
        },

        alertDetail: {
            original: "Original",
            translated: "Translated",
            disasterType: "Disaster Type",
            region: "Region",
            occurredAt: "Issued At",
            comments: "Comments",
        },

        community: {
            title: "Community",
        },

        settings: {
            title: "Settings",
            language: "Language",
        },

        footer: "© 2025 Disaster Alert Platform | Contact: lshlsh3690@gmail.com",

        metros: {
            "서울특별시": "Seoul", "부산광역시": "Busan", "대구광역시": "Daegu",
            "인천광역시": "Incheon", "광주광역시": "Gwangju", "대전광역시": "Daejeon",
            "울산광역시": "Ulsan", "세종특별자치시": "Sejong", "경기도": "Gyeonggi-do",
            "강원특별자치도": "Gangwon-do", "충청북도": "Chungbuk", "충청남도": "Chungnam",
            "전북특별자치도": "Jeonbuk", "전라남도": "Jeonnam", "경상북도": "Gyeongbuk",
            "경상남도": "Gyeongnam", "제주특별자치도": "Jeju-do",
        },

        disasterTypes: {
            "폭염": "Heat Wave", "기타": "Other", "호우": "Heavy Rain", "산불": "Wildfire", "대설": "Heavy Snow",
            "한파": "Cold Wave", "강풍": "Strong Wind", "화재": "Fire", "산사태": "Landslide", "홍수": "Flood",
            "가축질병": "Livestock Disease", "교통사고": "Traffic Accident", "건조": "Drought", "수도": "Water Supply", "풍랑": "Storm Surge",
            "교통통제": "Traffic Control", "전염병": "Epidemic", "미세먼지": "Fine Dust", "지진": "Earthquake", "정전": "Power Outage",
            "환경오염사고": "Environmental Pollution", "붕괴": "Collapse", "교통": "Traffic", "태풍": "Typhoon", "안개": "Fog",
            "황사": "Yellow Dust", "통신": "Communication", "민방공": "Civil Defense", "지진해일": "Tsunami", "가뭄": "Drought",
            "테러": "Terrorism", "금융": "Financial", "에너지": "Energy", "폭발": "Explosion", "AI": "AI",
        },

        levels: { "안전안내": "Safety Notice", "긴급재난": "Emergency Disaster", "위급재난": "Critical Disaster" },
        dashboard: {
            todayAlerts: "Today's Alerts",
            todayUserReports: "Today's Reports",
            totalUserReports: "Total User Reports",
            latestAlerts: "📩 Latest Alerts",
            alertMap: "🗺️ Regional Alert Map",
            latestComments: "💬 Latest Comments",
            viewAll: "View All →",
            noComments: "No recent comments.",
            anonymous: "Anonymous",
            langLabel: "Language",
            count: ""
        },
    },

    ja: {
        appName: "災害速報アーカイブ",
        loading: "読み込み中...",

        nav: {
            dashboard: "ダッシュボード",
            alerts: "災害速報",
            community: "コミュニティ",
            login: "ログイン",
            logout: "ログアウト",
            settings: "設定",
            user: "ユーザー",
        },

        home: {
            title: "災害速報アーカイブ",
            subtitle: "過去の災害速報を誰でも簡単に確認し、\n意見を共有できるプラットフォームです。",
            ctaTitle: "今すぐダッシュボードを確認",
            ctaButton: "災害速報を見る",
            features: {
                latest: { title: "最新の災害速報", desc: "全国から受信した災害速報を地域別に確認" },
                missing: { title: "行方不明者情報", desc: "警察庁公開の行方不明者データを統合検索" },
                stats: { title: "統計・グラフ", desc: "日別・地域別の災害アラート統計をビジュアルで確認" },
                community: { title: "コミュニティ報告", desc: "地域の被害報告やリアルタイムコメントを共有" },
            },
        },

        alertList: {
            title: "🗂 災害速報アーカイブ",
            description: "過去に受信した全ての災害速報一覧です。地域・日付・キーワードで検索できます。",
            report: "報告を投稿",
            search: "検索",
            searching: "検索中...",
            reset: "リセット",
            prev: "前へ",
            next: "次へ",
            filter: {
                sido: "都道府県（全て）",
                sigungu: "市区町村（全て）",
                type: "種類（全て）",
                level: "レベル（全て）",
                sourceAll: "出典（全て）",
                sourceOfficial: "公式のみ",
                sourceUser: "ユーザー報告のみ",
                keyword: "キーワード（例：警報）",
                startDate: "開始日",
                endDate: "終了日",
            },
        },

        alertDetail: {
            original: "原文",
            translated: "翻訳",
            disasterType: "災害種別",
            region: "地域",
            occurredAt: "発令時刻",
            comments: "コメント",
        },

        community: {
            title: "コミュニティ",
        },

        settings: {
            title: "設定",
            language: "言語設定",
        },

        footer: "© 2025 災害速報プラットフォーム | お問い合わせ: lshlsh3690@gmail.com",

        metros: {
            "서울특별시": "ソウル特別市", "부산광역시": "釜山広域市", "대구광역시": "大邱広域市",
            "인천광역시": "仁川広域市", "광주광역시": "光州広域市", "대전광역시": "大田広域市",
            "울산광역시": "蔚山広域市", "세종특별자치시": "世宗特別自治市", "경기도": "京畿道",
            "강원특별자치도": "江原特別自治道", "충청북도": "忠清北道", "충청남도": "忠清南道",
            "전북특별자치도": "全北特別自治道", "전라남도": "全羅南道", "경상북도": "慶尚北道",
            "경상남도": "慶尚南道", "제주특별자치도": "済州特別自治道",
        },

        disasterTypes: {
            "폭염": "猛暑", "기타": "その他", "호우": "大雨", "산불": "山火事", "대설": "大雪",
            "한파": "寒波", "강풍": "強風", "화재": "火災", "산사태": "土砂崩れ", "홍수": "洪水",
            "가축질병": "家畜疾病", "교통사고": "交通事故", "건조": "乾燥", "수도": "水道", "풍랑": "高波",
            "교통통제": "交通規制", "전염병": "感染症", "미세먼지": "PM2.5", "지진": "地震", "정전": "停電",
            "환경오염사고": "環境汚染事故", "붕괴": "崩壊", "교통": "交通", "태풍": "台風", "안개": "霧",
            "황사": "黄砂", "통신": "通信", "민방공": "民間防空", "지진해일": "津波", "가뭄": "干ばつ",
            "테러": "テロ", "금융": "金融", "에너지": "エネルギー", "폭발": "爆発", "AI": "AI",
        },

        levels: { "안전안내": "安全案内", "긴급재난": "緊急災害", "위급재난": "危急災害" },
        dashboard: {
            todayAlerts: "本日の災害速報",
            todayUserReports: "本日のユーザー報告",
            totalUserReports: "累積ユーザー報告数",
            latestAlerts: "📩 最新の災害速報",
            alertMap: "🗺️ 地域別災害速報マップ",
            latestComments: "💬 最新コメント",
            viewAll: "全て見る →",
            noComments: "最近のコメントはありません。",
            anonymous: "匿名",
            langLabel: "言語",
            count: "件"
        },
    },

    zh: {
        appName: "灾害预警档案",
        loading: "加载中...",

        nav: {
            dashboard: "仪表板",
            alerts: "灾害预警",
            community: "社区",
            login: "登录",
            logout: "退出",
            settings: "设置",
            user: "用户",
        },

        home: {
            title: "灾害预警档案",
            subtitle: "这是一个让任何人都能轻松查看过去灾害预警，\n并分享意见的平台。",
            ctaTitle: "立即查看仪表板",
            ctaButton: "查看灾害预警",
            features: {
                latest: { title: "最新预警", desc: "按地区查看全国收到的灾害预警" },
                missing: { title: "失踪人员信息", desc: "整合检索警察厅公开的失踪人员数据" },
                stats: { title: "统计与图表", desc: "可视化查看每日及地区灾害预警统计" },
                community: { title: "社区报告", desc: "分享地区受灾报告和实时评论" },
            },
        },

        alertList: {
            title: "🗂 灾害预警档案",
            description: "所有历史灾害预警列表，可按地区、日期或关键词搜索。",
            report: "提交报告",
            search: "搜索",
            searching: "搜索中...",
            reset: "重置",
            prev: "上一页",
            next: "下一页",
            filter: {
                sido: "省/市（全部）",
                sigungu: "市/区（全部）",
                type: "类型（全部）",
                level: "级别（全部）",
                sourceAll: "来源（全部）",
                sourceOfficial: "仅官方",
                sourceUser: "仅用户报告",
                keyword: "关键词（例：警报）",
                startDate: "开始日期",
                endDate: "结束日期",
            },
        },

        alertDetail: {
            original: "原文",
            translated: "翻译",
            disasterType: "灾害类型",
            region: "地区",
            occurredAt: "发布时间",
            comments: "评论",
        },

        community: {
            title: "社区",
        },

        settings: {
            title: "设置",
            language: "语言设置",
        },

        footer: "© 2025 灾害预警平台 | 联系方式: lshlsh3690@gmail.com",

        metros: {
            "서울특별시": "首尔特别市", "부산광역시": "釜山广域市", "대구광역시": "大邱广域市",
            "인천광역시": "仁川广域市", "광주광역시": "光州广域市", "대전광역시": "大田广域市",
            "울산광역시": "蔚山广域市", "세종특별자치시": "世宗特别自治市", "경기도": "京畿道",
            "강원특별자치도": "江原特别自治道", "충청북도": "忠清北道", "충청남도": "忠清南道",
            "전북특별자치도": "全北特别自治道", "전라남도": "全罗南道", "경상북도": "庆尚北道",
            "경상남도": "庆尚南道", "제주특별자치도": "济州特别自治道",
        },

        disasterTypes: {
            "폭염": "高温热浪", "기타": "其他", "호우": "暴雨", "산불": "山火", "대설": "大雪",
            "한파": "寒潮", "강풍": "大风", "화재": "火灾", "산사태": "山体滑坡", "홍수": "洪水",
            "가축질병": "畜禽疾病", "교통사고": "交通事故", "건조": "干燥", "수도": "供水", "풍랑": "风浪",
            "교통통제": "交通管制", "전염병": "传染病", "미세먼지": "细颗粒物", "지진": "地震", "정전": "停电",
            "환경오염사고": "环境污染事故", "붕괴": "倒塌", "교통": "交通", "태풍": "台风", "안개": "大雾",
            "황사": "沙尘暴", "통신": "通信", "민방공": "民防", "지진해일": "海啸", "가뭄": "干旱",
            "테러": "恐怖袭击", "금융": "金融", "에너지": "能源", "폭발": "爆炸", "AI": "AI",
        },

        levels: { "안전안내": "安全通知", "긴급재난": "紧急灾害", "위급재난": "危急灾害" },
        dashboard: {
            todayAlerts: "今日预警",
            todayUserReports: "今日用户报告",
            totalUserReports: "累计用户报告数",
            latestAlerts: "📩 最新预警",
            alertMap: "🗺️ 地区预警地图",
            latestComments: "💬 最新评论",
            viewAll: "查看全部 →",
            noComments: "暂无最新评论。",
            anonymous: "匿名",
            langLabel: "语言",
            count: "件"
        },
    },
} as const;

export type I18nKey = typeof i18n;