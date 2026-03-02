import React from "react";

const AdminBadge: React.FC = () => (
    <span
        style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            padding: "2px 8px",
            borderRadius: 999,
            background: "linear-gradient(135deg, #FF9800 0%, #FFD54F 100%)",
            color: "#fff",
            fontSize: 11,
            fontWeight: 700,
            letterSpacing: 0.5,
            boxShadow: "0 1px 4px rgba(255,152,0,0.3)",
            verticalAlign: "middle",
            lineHeight: 1.4,
        }}
    >
        ✨ Tribbae
    </span>
);

export default AdminBadge;
