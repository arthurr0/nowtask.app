UPDATE workspace_settings SET current_sprint = NULL WHERE current_sprint = 'S24';

UPDATE task SET sprint_code = NULL WHERE sprint_code = 'S24';
