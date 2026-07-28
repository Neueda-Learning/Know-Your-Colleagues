// ==========================================
// 1. 交易趋势折线图（线条无视比例，完全铺满自适应）
// ==========================================
const TransactionsLineChart = () => (
    <div style={{ width: '100%', height: '100%', minHeight: 100, display: 'flex', flexDirection: 'column' }}>
        {/* flex: 1 保证 SVG 区域充满父容器剩余的垂直空间 */}
        <div style={{ flex: 1, width: '100%', minHeight: 0, position: 'relative', overflow: 'hidden' }}>
            {/* preserveAspectRatio="none" 允许线条随窗口任意拉伸变形 */}
            <svg
                width="100%"
                height="100%"
                viewBox="0 0 500 110"
                preserveAspectRatio="none"
                style={{ display: 'block', overflow: 'hidden' }}
            >
                <defs>
                    <linearGradient id="transGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.20" />
                        <stop offset="100%" stopColor="#3B82F6" stopOpacity="0.00" />
                    </linearGradient>
                </defs>

                {/* 虚线网格 */}
                <line x1="30" y1="25" x2="470" y2="25" stroke="#F1F5F9" strokeDasharray="3 3" vectorEffect="non-scaling-stroke" />
                <line x1="30" y1="58" x2="470" y2="58" stroke="#F1F5F9" strokeDasharray="3 3" vectorEffect="non-scaling-stroke" />
                <line x1="30" y1="90" x2="470" y2="90" stroke="#F1F5F9" strokeDasharray="3 3" vectorEffect="non-scaling-stroke" />

                {/* 曲线与渐变 */}
                <path d="M 30,82 C 70,72 110,50 180,38 C 230,30 270,68 340,24 C 390,24 430,70 470,82 L 470,102 L 30,102 Z" fill="url(#transGrad)" />
                <path d="M 30,82 C 70,72 110,50 180,38 C 230,30 270,68 340,24 C 390,24 430,70 470,82" fill="none" stroke="#3B82F6" strokeWidth="2.5" strokeLinecap="round" vectorEffect="non-scaling-stroke" />

                {/* 数据点 */}
                {[{ x: 180, y: 38, val: '$510k' }, { x: 340, y: 24, val: '$680k' }].map((pt, i) => (
                    <g key={i}>
                        <circle cx={pt.x} cy={pt.y} r="3.5" fill="#FFFFFF" stroke="#3B82F6" strokeWidth="2" />
                        <text x={pt.x} y={pt.y - 8} fontSize="10" textAnchor="middle" fill={THEME.textSecondary} fontFamily="monospace" fontWeight="700">
                            {pt.val}
                        </text>
                    </g>
                ))}
            </svg>
        </div>

        {/* X轴标签 */}
        <div style={{
            display: 'flex',
            justify: 'space-between',
            width: '100%',
            padding: '0 6%',
            marginTop: '4px',
            fontSize: 'clamp(9px, 1.2vw, 11px)',
            color: THEME.textMuted,
            fontFamily: 'monospace',
            boxSizing: 'border-box'
        }}>
            <span>00:00</span><span>04:00</span><span>08:00</span><span>12:00</span><span>16:00</span><span>20:00</span><span>24:00</span>
        </div>
    </div>
);