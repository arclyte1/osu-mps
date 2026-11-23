# Версии данных матчей

| Версия | Данные | Отличие от следующей версии |
|---:|---|---|
| 1 | Хотя бы один `event.id` или `game.id` — внутренний отрицательный ключ. | В версии 2 все `event.id` и `game.id` — ID osu!. |
| 2 | Все `event.id` и `game.id` — ID osu! | В версии 3 подтверждены `game.event_id`, время начала и длительность игры, `score.mods`, `score.match_slot`, `score.match_team`, `score.match_pass` и история `match_title_change`. |
| 3 | Новые поля score, game.event_id| — |