# Voice-to-Text Quick Start (Vosk)

## 🎤 Быстрый старт за 3 шага

### Шаг 1: Скачайте модель Vosk

**Для английского:**
```bash
cd ~/.ai-advent-chat/vosk-models
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
unzip vosk-model-small-en-us-0.15.zip
```

**Для русского:**
```bash
cd ~/.ai-advent-chat/vosk-models
wget https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip
unzip vosk-model-small-ru-0.22.zip
```

**Windows (PowerShell):**
```powershell
mkdir "$env:USERPROFILE\.ai-advent-chat\vosk-models"
# Скачайте модель вручную с https://alphacephei.com/vosk/models
# Распакуйте в C:\Users\YourName\.ai-advent-chat\vosk-models\
```

### Шаг 2: Запустите приложение

```bash
./gradlew run
```

### Шаг 3: Включите в настройках

1. Откройте **Settings** (⚙️)
2. Включите **"Enable Voice-to-Text"**
3. Выберите язык (English, Русский и т.д.)
4. Проверьте статус:
   - ✅ **Model installed** - готово!
   - ⚠️ **Model not found** - скачайте модель
5. Нажмите **Save**

## ✨ Готово!

Кнопка микрофона 🎤 появится рядом с Send!

1. Нажмите 🎤
2. Говорите
3. Нажмите ⏹️
4. Текст автоматически отправится!

## 📥 Скачать модели

**Популярные модели:**

- 🇺🇸 English: https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip (40 MB)
- 🇷🇺 Russian: https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip (45 MB)
- 🇪🇸 Spanish: https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip (39 MB)
- 🇫🇷 French: https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip (41 MB)
- 🇩🇪 German: https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip (45 MB)

**Все модели:** https://alphacephei.com/vosk/models

## 💡 Преимущества Vosk

✅ **Бесплатно** - никаких API ключей или оплаты
✅ **Офлайн** - работает без интернета
✅ **Быстро** - распознавание за 1-3 секунды
✅ **Приватно** - данные не покидают ваш ПК
✅ **20+ языков** - большой выбор моделей

## ❓ Проблемы?

**"Model not found"?**
→ Проверьте путь: `~/.ai-advent-chat/vosk-models/`

**Плохо распознает?**
→ Говорите четче, используйте качественный микрофон

**Где скачать модели?**
→ https://alphacephei.com/vosk/models

## 📚 Полная документация

[VOICE_TO_TEXT_VOSK.md](VOICE_TO_TEXT_VOSK.md)

---

**Начните использовать голосовой ввод бесплатно!** 🎉
