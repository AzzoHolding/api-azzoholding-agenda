UPDATE item_menu
SET icon_key = 'Calendar',
    updated_at = NOW()
WHERE route = '/relatorio/agendamento';
