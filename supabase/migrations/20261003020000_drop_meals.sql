-- Meal plans are out of scope for this app (the spreadsheet's Food tab is not ported).
-- Both tables were empty when this ran; days_per_month only served meal cost math.

alter publication supabase_realtime drop table public.meal_items, public.meal_plans;
drop table public.meal_items;
drop table public.meal_plans;
alter table public.settings drop column days_per_month;
