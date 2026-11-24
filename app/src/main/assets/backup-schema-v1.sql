CREATE TABLE backup_info (
 id INTEGER PRIMARY KEY CHECK(id=1),
 format TEXT NOT NULL CHECK(format='jigeum-hana'),
 schema_version INTEGER NOT NULL CHECK(schema_version=1),
 app_version TEXT NOT NULL,
 created_at_epoch_ms INTEGER NOT NULL CHECK(created_at_epoch_ms>=0)
);
CREATE TABLE task_categories (
 id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL,
 position INTEGER NOT NULL UNIQUE CHECK(position>=0)
);
CREATE TABLE tasks (
 id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL,
 category_id TEXT NOT NULL REFERENCES task_categories(id),
 position INTEGER NOT NULL UNIQUE CHECK(position>=0),
 completed INTEGER NOT NULL CHECK(completed IN (0,1)),
 focus_mode TEXT NOT NULL CHECK(focus_mode IN ('unassigned','timer','stopwatch')),
 focus_minutes INTEGER NOT NULL CHECK(focus_minutes BETWEEN 0 AND 180),
 stopwatch_elapsed_ms INTEGER NOT NULL CHECK(stopwatch_elapsed_ms>=0),
 deleted_at_epoch_ms INTEGER NOT NULL CHECK(deleted_at_epoch_ms>=0),
 completed_at_epoch_ms INTEGER NOT NULL DEFAULT 0 CHECK(completed_at_epoch_ms>=0)
);
CREATE TABLE calendar_categories (
 id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL,
 color_argb INTEGER NOT NULL,
 position INTEGER NOT NULL UNIQUE CHECK(position>=0)
);
CREATE TABLE calendar_entries (
 id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL,
 category_id TEXT NOT NULL REFERENCES calendar_categories(id),
 start_date TEXT NOT NULL CHECK(length(start_date)=10),
 end_date TEXT NOT NULL CHECK(length(end_date)=10 AND end_date>=start_date),
 start_minute INTEGER, end_minute INTEGER,
 deleted_at_epoch_ms INTEGER NOT NULL CHECK(deleted_at_epoch_ms>=0),
 created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL,
 body_format TEXT NOT NULL,
 CHECK((start_minute IS NULL AND end_minute IS NULL) OR
 (start_minute IS NOT NULL AND end_minute IS NOT NULL AND start_minute BETWEEN 0 AND 1439 AND end_minute BETWEEN 0 AND 1439 AND (end_date>start_date OR end_minute>=start_minute)))
);
CREATE TABLE app_settings (
 id INTEGER PRIMARY KEY CHECK(id=1),
 default_focus_mode TEXT NOT NULL CHECK(default_focus_mode IN ('last','unassigned','stopwatch')),
 font_step INTEGER NOT NULL CHECK(font_step BETWEEN 0 AND 8),
 last_minutes INTEGER NOT NULL CHECK(last_minutes BETWEEN 0 AND 180),
 last_stopwatch INTEGER NOT NULL CHECK(last_stopwatch IN (0,1)),
 wallpaper_enabled INTEGER NOT NULL CHECK(wallpaper_enabled IN (0,1)),
 wallpaper_color_argb INTEGER NOT NULL,
 calendar_hide_uncategorized INTEGER NOT NULL DEFAULT 0 CHECK(calendar_hide_uncategorized IN (0,1)),
 completed_tasks_last INTEGER NOT NULL DEFAULT 1 CHECK(completed_tasks_last IN (0,1)),
 checklist_start_mode TEXT NOT NULL DEFAULT 'last' CHECK(checklist_start_mode IN ('default','first','last')),
 calendar_start_mode TEXT NOT NULL DEFAULT 'first' CHECK(calendar_start_mode IN ('default','first','last')),
 last_calendar_category_id TEXT NOT NULL DEFAULT ''
);
CREATE TABLE focus_presets (
 position INTEGER PRIMARY KEY CHECK(position BETWEEN 0 AND 2),
 minutes INTEGER NOT NULL CHECK(minutes BETWEEN 0 AND 180)
);
CREATE TABLE focus_state (
 id INTEGER PRIMARY KEY CHECK(id=1),
 selected_task_id TEXT REFERENCES tasks(id),
 selected_category_id TEXT NOT NULL REFERENCES task_categories(id),
 timer_task_id TEXT REFERENCES tasks(id), title TEXT NOT NULL,
 mode TEXT NOT NULL CHECK(mode IN ('timer','stopwatch')),
 status TEXT NOT NULL CHECK(status IN ('idle','paused','finished')),
 was_running INTEGER NOT NULL CHECK(was_running IN (0,1)),
 remaining_ms INTEGER NOT NULL CHECK(remaining_ms>=0),
 elapsed_ms INTEGER NOT NULL CHECK(elapsed_ms>=0)
);
CREATE INDEX tasks_by_category ON tasks(category_id,deleted_at_epoch_ms,position);
CREATE INDEX calendar_by_date ON calendar_entries(deleted_at_epoch_ms,start_date,end_date);
