CREATE INDEX IF NOT EXISTS idx_appointments_heatmap_tenant_professional_date
    ON appointments (tenant_id, professional_id, date);
