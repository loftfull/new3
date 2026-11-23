# Knowledge Hub AI — визуальное направление

**Статус** · Эталон S01-v1 утверждён владельцем 2026-11-23. Выбран вариант 01 «Недавние».

Дизайн-пакет исследует главную страницу S01 для Android. Десять концепций используют общий мобильный экран 390 × 845 dp в масштабе 2×. Материалы и значения в макетах демонстрационные. Исходные PNG предыдущего проектирования отсутствуют в проверенной ветке `origin/design/knowledge-hub-foundation` и в предоставленных файлах рабочей среды. Отправная точка — [описание дизайн-системы в проектном пакете](https://github.com/loftfull/new3/blob/design/knowledge-hub-foundation/docs/DESIGN_SYSTEM.md).

## Кандидаты

| № | Вариант | Полноразмерный PNG | Редактируемый источник |
| --- | --- | --- | --- |
| 01 | Недавние | [PNG](design/concepts/01/screen.png) | [HTML](design/concepts/01/index.html) |
| 02 | Темы | [PNG](design/concepts/02/screen.png) | [HTML](design/concepts/02/index.html) |
| 03 | Хронология | [PNG](design/concepts/03/screen.png) | [HTML](design/concepts/03/index.html) |
| 04 | Продолжить чтение | [PNG](design/concepts/04/screen.png) | [HTML](design/concepts/04/index.html) |
| 05 | Входящие | [PNG](design/concepts/05/screen.png) | [HTML](design/concepts/05/index.html) |
| 06 | Источники | [PNG](design/concepts/06/screen.png) | [HTML](design/concepts/06/index.html) |
| 07 | Визуальная сетка | [PNG](design/concepts/07/screen.png) | [HTML](design/concepts/07/index.html) |
| 08 | Текущий проект | [PNG](design/concepts/08/screen.png) | [HTML](design/concepts/08/index.html) |
| 09 | Поиск | [PNG](design/concepts/09/screen.png) | [HTML](design/concepts/09/index.html) |
| 10 | Заметка и связи | [PNG](design/concepts/10/screen.png) | [HTML](design/concepts/10/index.html) |

Предложение дизайнера — **01 «Недавние»**. Последнее сохранение сразу видно, поиск доступен одной рукой через нижнюю панель, результаты удобно читать по длинным русским заголовкам. Для преимущественно поискового сценария лучший отдельный кандидат — **09 «Поиск»**.

[Матрица Innovation Gate](design/reviews/INNOVATION_GATE.md) · [Токены](design/tokens.json) · [Реестр компонентов](design/COMPONENTS.md) · [Манифест и SHA-256](design/manifest.json)

## Reference Lock · S01-v1

Владелец выбрал вариант 01 «Недавние» 2026-11-23 в диалоге миссии. Codex записал решение по визуальному направлению экрана S01. Исходные байты находятся в [коммите 4ee8dab](https://github.com/loftfull/new3/commit/4ee8dabfb9cec263d191b7956166e084c99c6cd1).

| Файл | SHA-256 |
| --- | --- |
| [PNG S01](design/concepts/01/screen.png) | `7ae30704e31c7df6bbb3486393fcbe5117e15475dbc465315249c7b6bf7ae98b` |
| [Редактируемый исходник S01](design/concepts/01/index.html) | `801a94e8eeeeab4959681e65a7a7b2742d3d72cb15850a12d706e0cb1308c67f` |
| [Общий CSS](design/concepts/library.css) | `dcf58ac340bd9d8b5d4c41e8392bdaed12f19d4269a013ab97e32cfc8a39bdff` |
| [Токены](design/tokens.json) | `9d0da80255c619ca71372281983653a2b70535202175969f1c2ab70ba60d81c4` |

S02–S28 проектируются на основе выбранного направления. У каждого экрана будет собственный артефакт и статус в реестре. Статус визуальной приёмки Android привязывается к ревизии, скриншоту и отдельному отчёту Visual QA.
