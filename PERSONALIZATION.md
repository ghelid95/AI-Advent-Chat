# Developer Personalization Guide

## Обзор

Система персонализации позволяет настроить взаимодействие с LLM в соответствии с вашими предпочтениями как разработчика. LLM будет учитывать ваши архитектурные предпочтения, стиль кодирования, используемые инструменты и принципы разработки при генерации ответов и кода.

## Как это работает

При включении персонализации система автоматически дополняет system prompt информацией о ваших предпочтениях. Это помогает LLM:
- Генерировать код в вашем стиле
- Предлагать решения, соответствующие вашей архитектуре
- Использовать знакомые вам инструменты и технологии
- Следовать вашим принципам разработки

## Настройка персонализации

### Вариант 1: Редактирование конфигурационного файла

В корне проекта создан файл `developer-personalization.json` с вашими текущими настройками:

```json
{
  "enabled": true,
  "architecture": {
    "primaryPattern": "MVVM (Model-View-ViewModel)",
    "errorHandling": "Комбинированный подход",
    "projectStructure": "Модульная архитектура",
    "dependencyInjection": "Dagger 2"
  },
  "codingStyle": {
    "programmingParadigm": "Смешанный стиль",
    "asyncApproach": "Coroutines с suspend функциями",
    "namingConventions": [
      "camelCase для переменных",
      "Говорящие имена",
      "Не делать интерфейсы для 1 реализации"
    ]
  },
  "tools": {
    "primaryLanguage": "Kotlin",
    "frameworks": [
      "Kotlin Coroutines",
      "Jetpack Compose / Compose Desktop",
      "Ktor",
      "Gradle Kotlin DSL",
      "Dagger 2"
    ],
    "buildSystem": "Gradle Kotlin DSL"
  },
  "practices": {
    "principles": [
      "DRY (Don't Repeat Yourself)",
      "KISS (Keep It Simple, Stupid)",
      "YAGNI (You Aren't Gonna Need It)"
    ],
    "testingApproach": [
      "Integration тесты"
    ],
    "documentationLevel": "KDoc для public API"
  },
  "responseStyle": {
    "detailLevel": "Сбалансированные ответы",
    "includeExamples": true,
    "explainTradeoffs": true
  }
}
```

### Вариант 2: Редактирование через app-settings.json

Настройки также хранятся в `~/.ai-advent-chat/app-settings.json` в разделе `developerPersonalization`. Приоритет имеет файл `developer-personalization.json`, если он существует и `enabled: true`.

## Структура конфигурации

### Architecture Preferences (Архитектурные предпочтения)

- **primaryPattern**: Основной архитектурный паттерн (MVVM, Clean Architecture, MVI, и т.д.)
- **errorHandling**: Подход к обработке ошибок (Result types, Exceptions, Sealed classes)
- **projectStructure**: Структура проекта (По слоям, по фичам, модульная)
- **dependencyInjection**: Система DI (Dagger 2, Koin, Manual DI)

### Coding Style Preferences (Стиль кодирования)

- **programmingParadigm**: Парадигма программирования (Функциональный, ООП, Смешанный)
- **asyncApproach**: Подход к асинхронности (Coroutines + Flow, suspend functions, RxJava)
- **namingConventions**: Соглашения по именованию (массив строк)

### Tools Preferences (Инструменты)

- **primaryLanguage**: Основной язык программирования
- **frameworks**: Список используемых фреймворков и библиотек
- **buildSystem**: Система сборки

### Development Practices (Практики разработки)

- **principles**: Принципы разработки (SOLID, DRY, KISS, YAGNI)
- **testingApproach**: Подход к тестированию (Unit, Integration, UI tests, TDD)
- **documentationLevel**: Уровень документирования кода

### Response Style Preferences (Стиль ответов)

- **detailLevel**: Уровень детализации ("Краткие ответы", "Детальные объяснения", "Сбалансированные ответы", "Контекстно")
- **includeExamples**: Включать ли примеры кода (true/false)
- **explainTradeoffs**: Объяснять ли компромиссы разных подходов (true/false)

## Включение/Отключение персонализации

Чтобы включить персонализацию, установите `"enabled": true` в конфигурационном файле.

Чтобы отключить, установите `"enabled": false`. При отключении будет использоваться обычный system prompt без персонализации.

## Примеры использования

### Пример 1: Запрос на создание нового компонента

**Без персонализации:**
```
Создай новый репозиторий для работы с пользователями
```

**С персонализацией:**
LLM учтет, что вы используете:
- MVVM архитектуру
- Dagger 2 для DI
- Coroutines для асинхронности
- Модульную структуру

И создаст код соответствующий вашим предпочтениям.

### Пример 2: Вопрос об архитектуре

**Запрос:**
```
Как лучше организовать обработку ошибок в этом проекте?
```

**С персонализацией:**
LLM предложит комбинированный подход (так как это ваша настройка) и объяснит trade-offs разных решений (так как у вас `explainTradeoffs: true`).

## Обновление настроек

1. Отредактируйте `developer-personalization.json`
2. Перезапустите приложение
3. Новые настройки будут применены автоматически

## Расположение файлов

- **Проектный конфиг**: `./developer-personalization.json` (в корне проекта)
- **Глобальный конфиг**: `~/.ai-advent-chat/app-settings.json` (раздел `developerPersonalization`)

## Приоритет настроек

1. Файл `developer-personalization.json` в текущей директории (если `enabled: true`)
2. Настройки из `app-settings.json`

## Интеграция с существующими функциями

Персонализация работает совместно с:
- Code Assistant (автоконтекст)
- Git Integration
- MCP Tools
- Project Documentation RAG
- Chat Commands

Все эти функции учитывают ваши персональные настройки при генерации ответов.

## Советы по настройке

1. **Будьте специфичны**: Чем точнее описаны ваши предпочтения, тем лучше результат
2. **Обновляйте регулярно**: По мере изменения ваших практик обновляйте конфигурацию
3. **Экспериментируйте**: Попробуйте разные уровни детализации ответов
4. **Комбинируйте**: Используйте персонализацию вместе с custom system prompts

## Примеры настроек для разных типов проектов

### Android приложение
```json
{
  "architecture": {
    "primaryPattern": "MVVM",
    "dependencyInjection": "Hilt"
  },
  "tools": {
    "frameworks": ["Jetpack Compose", "Room", "Retrofit", "Coroutines"]
  }
}
```

### Backend сервис
```json
{
  "architecture": {
    "primaryPattern": "Clean Architecture",
    "dependencyInjection": "Koin"
  },
  "tools": {
    "frameworks": ["Ktor", "Exposed", "Koin", "Coroutines"]
  }
}
```

### Desktop приложение (текущая конфигурация)
```json
{
  "architecture": {
    "primaryPattern": "MVVM",
    "dependencyInjection": "Dagger 2"
  },
  "tools": {
    "frameworks": ["Compose Desktop", "Ktor", "Coroutines"]
  }
}
```

## Устранение неполадок

**Персонализация не работает:**
1. Проверьте, что `"enabled": true`
2. Убедитесь, что JSON файл корректен (используйте JSON validator)
3. Проверьте консоль на наличие ошибок загрузки
4. Перезапустите приложение

**Неожиданное поведение LLM:**
1. Проверьте настройки `responseStyle`
2. Убедитесь, что `detailLevel` соответствует вашим ожиданиям
3. Попробуйте отключить персонализацию для сравнения

## Дополнительная информация

Для более глубокой настройки вы можете модифицировать класс `PersonalizationService` в файле:
`src/main/kotlin/data/DeveloperPersonalization.kt`

Там можно изменить формат генерируемого system prompt и добавить дополнительные секции.
