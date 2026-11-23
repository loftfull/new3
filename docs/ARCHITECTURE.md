# Архитектура — предварительная спецификация

Статус: проектное предложение; исходный код приложения ещё не предоставлен.

## Уровни
1. Capture: ACTION_SEND / ACTION_SEND_MULTIPLE, ссылки, файлы, текст, заметки.
2. Ingestion: нормализация URL, метаданные, локальная транзакционная запись.
3. Processing: очередь задач, извлечение доступного текста, AI-резюме, теги, дубликаты, связи.
4. Storage: материалы, источники, коллекции, темы, теги, заметки, проекты, история, версии.
5. Retrieval: полнотекстовый поиск, фильтры, опциональная семантика.
6. Experience: библиотека, коллекции, поиск, чтение, AI, настройки.

## Сущности
Material(id, type, title, canonicalUrl?, originalUrls[], sourceId?, status, createdAt, updatedAt, rawMetadata, extractedTextRef?)
Collection(id, name, kind[manual|smart], rules?)
Topic(id, name, aliases[])
Tag(id, name)
MaterialTopic(materialId, topicId, origin[manual|ai], confidence?)
MaterialTag(materialId, tagId)
CollectionMembership(collectionId, materialId, origin)
Note(id, materialId?, body, createdAt, updatedAt)
MaterialRelation(sourceId, targetId, relationType, provenance)
ProcessingJob(id, materialId, stage, status, attempts, error?)
AuditEvent(id, entityId, action, timestamp, beforeRef?, afterRef?)

## Инварианты
- Сначала сохранить исходный материал, затем запускать AI.
- Ошибка AI не удаляет материал.
- Не путать дубликаты с семантически связанными материалами.
- Не удалять пользовательские заметки при объединении.
- Ручные классификации имеют приоритет над AI.
- Никакой отправки личных данных внешним моделям без разрешения.

## Технологический выбор
Если нужен нативный Android rebuild: Kotlin, Jetpack Compose, Room, DataStore, WorkManager, Paging, Android Sharesheet. Решение принимается после аудита стека.
