# Cure Drucik — RAG dla dokumentacji serwisowej ciężarówek i maszyn (wersja publiczna)

Aplikacja Android i serwer, które odpowiadają na pytania mechanika na podstawie książek
serwisowych: momenty dokręcania, luzy, kody usterek (DTC, MID/SID/FMI), numery przewodów,
schematy. Każda odpowiedź podaje źródło: plik PDF i stronę. Gdy dokumentacja nie zawiera
danych, system mówi to wprost zamiast zgadywać.

- `app/` — aplikacja Android (Kotlin, Jetpack Compose): czat, wybór modelu, podgląd PDF
- `server/rag_server_fast.py` — serwer FastAPI: indeks stron PDF, wyszukiwanie fragmentów,
  zapytanie do lokalnego modelu (LM Studio: Bielik 11B, Qwen) z kontekstem dokumentacji

Baza testowa: kilkaset instrukcji serwisowych (m.in. Iveco Stralis, Volvo, Renault Trucks,
Scania, JCB, Komatsu, ogrzewania postojowe). Same PDF-y nie są częścią repozytorium.

**Status:** projekt w rozwoju. Obecne wyszukiwanie jest słownikowe (słowa kluczowe + marka
z nazwy pliku); następny krok to wyszukiwanie wektorowe i lepsze dzielenie stron.

**Jak powstał:** kod napisały agenty AI (Antigravity, Claude Code). Dawid Tutak, mechanik
i diagnosta, zdefiniował zakres, przygotował bazę dokumentacji i testował odpowiedzi.

## Licencja
Kod udostępniony do wglądu jako portfolio. Wszelkie prawa zastrzeżone © F.W. DRUCIK Dawid Tutak.
