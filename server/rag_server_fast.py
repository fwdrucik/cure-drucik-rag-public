import os
import sys
import re
import json
import time
import gc
import subprocess
import requests
import pymupdf
from typing import List, Optional
from fastapi import FastAPI, HTTPException, Query, Body, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

app = FastAPI(title="Warsztat RAG AI Server", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

PDF_DIR = r"D:\03_PDF"
CACHE_FILE = os.path.join(PDF_DIR, ".rag_cache.json")
LM_STUDIO_URL = "http://127.0.0.1:1234/v1"

documents_db = []
chunks_db = []
doc_pages_map = {}
is_indexed = False

def extract_text_and_index(pdf_path: str, max_pages: int = 80):
    pages_data = []
    try:
        doc = pymupdf.open(pdf_path)
        total_pages = len(doc)
        limit = min(total_pages, max_pages)
        for page_num in range(limit):
            page = doc.load_page(page_num)
            text = page.get_text("text")
            if text and len(text.strip()) > 30:
                pages_data.append({"page": page_num + 1, "text": text.strip()})
        doc.close()
        return total_pages, pages_data
    except Exception as e:
        print(f"[RAG] Błąd parsowania {pdf_path}: {e}")
        return 0, []

def chunk_text(text: str, chunk_size: int = 500, overlap: int = 100):
    words = text.split()
    if len(words) <= chunk_size:
        return [" ".join(words)]
    chunks = []
    start = 0
    while start < len(words):
        end = start + chunk_size
        chunk = " ".join(words[start:end])
        chunks.append(chunk)
        start += (chunk_size - overlap)
    return chunks

def build_or_load_index():
    global documents_db, chunks_db, doc_pages_map, is_indexed
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                documents_db = data.get("documents", [])
                chunks_db = data.get("chunks", [])
                doc_pages_map = data.get("doc_pages_map", {})
                is_indexed = True
                print(f"[RAG] Załadowano z cache: {len(documents_db)} dokumentów, {len(chunks_db)} fragmentów.")
                return
        except Exception as e:
            print(f"[RAG] Błąd cache: {e}. Przebudowuję...")

    pdf_files = [f for f in os.listdir(PDF_DIR) if f.lower().endswith(".pdf")]
    docs = []
    chunks = []
    pages_map = {}
    
    for idx, fname in enumerate(pdf_files):
        fpath = os.path.join(PDF_DIR, fname)
        size_kb = round(os.path.getsize(fpath) / 1024, 1)
        
        fn_lower = fname.lower()
        brand = "Inne"
        if "jcb" in fn_lower: brand = "JCB"
        elif "komatsu" in fn_lower: brand = "Komatsu"
        elif "volvo" in fn_lower: brand = "Volvo"
        elif "iveco" in fn_lower: brand = "Iveco"
        elif "renault" in fn_lower: brand = "Renault Trucks"
        elif "scania" in fn_lower: brand = "Scania"
        elif "eberspacher" in fn_lower or "d1lc" in fn_lower or "d5ws" in fn_lower: brand = "Eberspächer"
        elif "webasto" in fn_lower: brand = "Webasto"
        elif "cnc" in fn_lower or "mach" in fn_lower or "csmio" in fn_lower: brand = "CNC / Automatyka"
        elif "groeneveld" in fn_lower or "bekamax" in fn_lower or "twin" in fn_lower: brand = "Centralne Smarowanie"
        elif "faac" in fn_lower or "proteco" in fn_lower or "bram" in fn_lower: brand = "Centrale Bramowe"

        total_pages, pages = extract_text_and_index(fpath, max_pages=60 if size_kb > 20000 else 120)
        pages_map[fname] = total_pages

        doc_entry = {"id": idx + 1, "filename": fname, "brand": brand, "size_kb": size_kb, "total_pages": total_pages}
        docs.append(doc_entry)

        for p in pages:
            p_chunks = chunk_text(p["text"])
            for c in p_chunks:
                if len(c.strip()) > 50:
                    chunks.append({
                        "doc_id": idx + 1,
                        "filename": fname,
                        "brand": brand,
                        "page": p["page"],
                        "total_pages": total_pages,
                        "text": c
                    })

    documents_db = docs
    chunks_db = chunks
    doc_pages_map = pages_map
    is_indexed = True

    try:
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump({
                "documents": documents_db,
                "chunks": chunks_db,
                "doc_pages_map": doc_pages_map
            }, f, ensure_ascii=False)
    except Exception:
        pass

# ----------------- MULTILINGUAL DICTIONARY & EXPANSION -----------------

TECH_DICTIONARY = {
    "moment": ["torque", "tightening", "anzugsmoment", "drehmoment", "nm", "ft-lb"],
    "momenty": ["torque", "tightening", "anzugsmoment", "drehmoment", "nm", "ft-lb"],
    "momentu": ["torque", "tightening", "anzugsmoment", "drehmoment", "nm", "ft-lb"],
    "dokrecania": ["tightening", "torque", "tighten", "step", "angle", "anzug", "drehmoment", "nm"],
    "dokrecanie": ["tightening", "torque", "tighten", "step", "angle", "anzug", "drehmoment", "nm"],
    "dokrecenia": ["tightening", "torque", "tighten", "step", "angle", "anzug", "drehmoment", "nm"],
    "dokrecic": ["tightening", "torque", "tighten", "step", "angle", "anzug", "drehmoment", "nm"],
    "glowica": ["cylinder head", "head", "zylinderkopf", "culasse", "testata"],
    "glowicy": ["cylinder head", "head", "zylinderkopf", "culasse", "testata"],
    "glowice": ["cylinder head", "head", "zylinderkopf", "culasse", "testata"],
    "luz": ["clearance", "lash", "gap", "spiel", "ventilspiel", "gioco"],
    "luzy": ["clearance", "lash", "gap", "spiel", "ventilspiel", "gioco"],
    "luzow": ["clearance", "lash", "gap", "spiel", "ventilspiel", "gioco"],
    "zaworowy": ["valve", "valves", "ventil", "ventile", "valvola"],
    "zaworowe": ["valve", "valves", "ventil", "ventile", "valvola"],
    "zaworow": ["valve", "valves", "ventil", "ventile", "valvola"],
    "zawory": ["valve", "valves", "ventil", "ventile", "valvola"],
    "zawor": ["valve", "valves", "ventil", "ventile", "valvola"],
    "ssacy": ["intake", "inlet", "einlass", "aspirazione"],
    "ssace": ["intake", "inlet", "einlass", "aspirazione"],
    "ssacych": ["intake", "inlet", "einlass", "aspirazione"],
    "dolotowy": ["intake", "inlet", "einlass"],
    "wydechowy": ["exhaust", "auslass", "scarico"],
    "wydechowe": ["exhaust", "auslass", "scarico"],
    "wydechowych": ["exhaust", "auslass", "scarico"],
    "wtrysk": ["injector", "injectors", "injection", "injektor", "düse", "düsen"],
    "wtryski": ["injector", "injectors", "injection", "injektor", "düse", "düsen"],
    "wtryskiwacz": ["injector", "injectors", "injection", "unit injector", "injektor"],
    "wtryskiwacze": ["injector", "injectors", "injection", "unit injector", "injektor"],
    "wtryskiwaczy": ["injector", "injectors", "injection", "unit injector", "injektor"],
    "paliwo": ["fuel", "diesel", "kraftstoff", "carburante"],
    "paliwowy": ["fuel", "diesel", "kraftstoff", "carburante"],
    "paliwowego": ["fuel", "diesel", "kraftstoff", "carburante"],
    "odpowietrzanie": ["bleeding", "bleed", "priming", "entlüftung"],
    "odpowietrzyc": ["bleeding", "bleed", "priming", "entlüftung"],
    "rozrzad": ["timing", "camshaft", "timing gear", "steuerkette", "zahnriemen"],
    "rozrzadu": ["timing", "camshaft", "timing gear", "steuerkette", "zahnriemen"],
    "walek": ["camshaft", "nockenwelle", "albero a camme"],
    "walka": ["camshaft", "nockenwelle", "albero a camme"],
    "wal": ["crankshaft", "kurbelwelle", "albero a gomiti"],
    "walu": ["crankshaft", "kurbelwelle", "albero a gomiti"],
    "korbowy": ["crankshaft", "kurbelwelle", "albero a gomiti"],
    "korbowego": ["crankshaft", "kurbelwelle", "albero a gomiti"],
    "panewka": ["bearing", "bearings", "lager", "cuscinetto"],
    "panewki": ["bearing", "bearings", "lager", "main bearing", "rod bearing"],
    "panewek": ["bearing", "bearings", "lager", "main bearing", "rod bearing"],
    "tlok": ["piston", "kolben", "pistone"],
    "tloka": ["piston", "kolben", "pistone"],
    "tloki": ["piston", "pistons", "kolben"],
    "pierscienie": ["piston ring", "rings", "kolbenringe"],
    "uszczelka": ["gasket", "seal", "dichtung", "guarnizione"],
    "uszczelki": ["gasket", "seal", "dichtung", "guarnizione"],
    "pompa": ["pump", "pumpe", "pompa"],
    "pompy": ["pump", "pumpe", "pompa"],
    "olej": ["oil", "engine oil", "lube", "lubrication", "öl", "olio"],
    "oleju": ["oil", "engine oil", "lube", "lubrication", "öl", "olio"],
    "plyn": ["coolant", "fluid", "kühlerflüssigkeit", "liquido"],
    "chlodzenie": ["cooling", "coolant", "radiator", "kühler"],
    "chlodnicy": ["radiator", "coolant", "kühler"],
    "filtr": ["filter", "filters"],
    "filtra": ["filter", "filters"],
    "filtry": ["filter", "filters"],
    "blad": ["fault", "code", "dtc", "spn", "fmi", "error", "fehler", "guasto"],
    "bledy": ["fault", "codes", "dtc", "spn", "fmi", "trouble codes", "fehler"],
    "bledow": ["fault", "codes", "dtc", "spn", "fmi", "trouble codes", "fehler"],
    "kod": ["fault", "code", "dtc", "spn", "fmi", "code"],
    "kody": ["fault", "codes", "dtc", "spn", "fmi", "codes"],
    "kodow": ["fault", "codes", "dtc", "spn", "fmi", "codes"],
    "schemat": ["schematic", "diagram", "wiring", "circuit", "harness", "schaltplan", "schema"],
    "schematy": ["schematics", "diagrams", "wiring", "circuits", "schaltplan"],
    "schematu": ["schematic", "diagram", "wiring", "circuit", "schaltplan"],
    "elektryka": ["electrical", "electric", "wiring", "wire", "cable", "harness"],
    "elektryczny": ["electrical", "electric", "wiring", "wire", "cable", "harness"],
    "przewod": ["wire", "cable", "leitung", "cavo"],
    "przewody": ["wires", "cables", "wiring", "leitungen"],
    "przewodow": ["wires", "cables", "wiring", "leitungen"],
    "bezpiecznik": ["fuse", "sicherung", "fusibile"],
    "bezpieczniki": ["fuses", "sicherungen", "fusibili"],
    "przekaznik": ["relay", "relais"],
    "przekazniki": ["relays", "relais"],
    "hydraulika": ["hydraulic", "hydraulics", "hydraulik", "idraulica"],
    "hydrauliki": ["hydraulic", "hydraulics", "hydraulik", "idraulica"],
    "hydrauliczny": ["hydraulic", "pump", "valve", "spool", "cylinder"],
    "rozdzielacz": ["control valve", "main valve", "spool valve", "steuerventil"],
    "skrzynia": ["transmission", "gearbox", "getriebe", "cambio"],
    "skrzyni": ["transmission", "gearbox", "getriebe", "cambio"],
    "biegow": ["transmission", "gearbox", "gears", "getriebe"],
    "sprzeglo": ["clutch", "kupplung", "frizione"],
    "sprzegla": ["clutch", "kupplung", "frizione"],
    "hamulec": ["brake", "brakes", "bremse", "freno"],
    "hamulce": ["brake", "brakes", "bremse", "freni"],
    "hamulcowy": ["brake", "brakes", "bremse"],
    "zacisk": ["caliper", "brake caliper", "bremssattel"],
    "regulacja": ["adjustment", "adjust", "setting", "einstellung"],
    "regulacji": ["adjustment", "adjust", "setting", "einstellung"],
    "cisnienie": ["pressure", "druck", "pressione", "bar", "psi", "kpa"],
    "cisnienia": ["pressure", "druck", "pressione", "bar", "psi", "kpa"],
    "temperatura": ["temperature", "temperatur", "temperatura"],
    "czujnik": ["sensor", "transducer", "geber", "fühler", "sensore"],
    "czujnika": ["sensor", "transducer", "geber", "fühler", "sensore"],
    "czujniki": ["sensors", "transducers", "geber", "sensori"],
    "rozrusznik": ["starter", "starter motor", "anlasser", "motorino avviamento"],
    "alternator": ["alternator", "generator", "lichtmaschine"],
    "turbina": ["turbocharger", "turbo", "turbolader"],
    "turbosprezarka": ["turbocharger", "turbo", "turbolader"],
    "zbiornik": ["tank", "reservoir", "behälter", "serbatoio"]
}

def remove_polish_accents(text: str) -> str:
    mapping = {
        'ą': 'a', 'ć': 'c', 'ę': 'e', 'ł': 'l', 'ń': 'n',
        'ó': 'o', 'ś': 's', 'ź': 'z', 'ż': 'z',
        'Ą': 'A', 'Ć': 'C', 'Ę': 'E', 'Ł': 'L', 'Ń': 'N',
        'Ó': 'O', 'Ś': 'S', 'Ź': 'Z', 'Ż': 'Z'
    }
    return ''.join(mapping.get(c, c) for c in text)

def search_relevant_chunks(query: str, top_k: int = 5):
    if not chunks_db: return []
    
    clean_q = remove_polish_accents(query.lower())
    raw_tokens = [w.lower() for w in re.findall(r"\w+", clean_q) if len(w) > 1]
    if not raw_tokens: return chunks_db[:top_k]

    # Multilingual expansion
    expanded_tokens = set(raw_tokens)
    for token in raw_tokens:
        if token in TECH_DICTIONARY:
            for syn in TECH_DICTIONARY[token]:
                for word in syn.split():
                    if len(word) > 2:
                        expanded_tokens.add(word.lower())

    # Detect Vehicle / Model brand focus
    brand_keywords = {
        "volvo": ["volvo", "d12", "d12d", "d12a", "d12c", "d13", "d16", "fh", "fm", "l150", "l180"],
        "jcb": ["jcb", "3cx", "4cx", "js", "528", "fastrac", "perkins", "dieselmax"],
        "komatsu": ["komatsu", "pc", "pc340", "pc210", "pc290", "pc300", "pc400", "wa380", "wa470"],
        "iveco": ["iveco", "stralis", "trakker", "eurocargo", "daily", "cursor", "ebs"],
        "renault": ["renault", "premium", "magnum", "kerax", "dxi", "midlum"],
        "scania": ["scania", "r420", "r440", "r500", "hpi", "pde", "dc12", "dc13"],
        "eberspacher": ["eberspacher", "d1lc", "d3lc", "d5ws", "hydronic", "airtronic"],
        "webasto": ["webasto", "air top", "thermo top", "at2000"],
        "faac": ["faac", "748", "740", "741", "452", "455", "e045"],
        "isuzu": ["isuzu", "4hk1", "6hk1", "6wg1", "hitachi"]
    }

    target_brands = []
    for brand, aliases in brand_keywords.items():
        if any(a in clean_q for a in aliases):
            target_brands.append(brand)

    # Check for specific phrase combinations
    is_torque_head_query = any(w in expanded_tokens for w in ["torque", "tighten", "tightening", "anzug"]) and \
                           any(w in expanded_tokens for w in ["head", "cylinder", "zylinderkopf"])
    
    is_valve_clearance_query = any(w in expanded_tokens for w in ["clearance", "lash", "ventilspiel", "luz"]) and \
                               any(w in expanded_tokens for w in ["valve", "valves", "ventil", "zawor"])

    scored_chunks = []
    for c in chunks_db:
        text_lower = c["text"].lower()
        fn_lower = c["filename"].lower()
        score = 0

        # Brand / Vehicle Match Bonus
        if target_brands:
            c_brand = c.get("brand", "").lower()
            for b in target_brands:
                if b in c_brand or b in fn_lower:
                    score += 25
                elif b in text_lower:
                    score += 10
        
        # Token matching
        for token in expanded_tokens:
            count = text_lower.count(token)
            if count > 0:
                score += min(count * 2, 16)
            if token in fn_lower:
                score += 10

        # High-value phrase boosts
        if is_torque_head_query:
            if ("cylinder head" in text_lower or "zylinderkopf" in text_lower) and ("tightening" in text_lower or "torque" in text_lower or "anzug" in text_lower):
                score += 40
            if "step 1" in text_lower or "nm" in text_lower or "ft-lb" in text_lower:
                score += 20

        if is_valve_clearance_query:
            if ("valve clearance" in text_lower or "ventilspiel" in text_lower or "valve lash" in text_lower):
                score += 40
            if "inlet" in text_lower or "exhaust" in text_lower or "mm" in text_lower:
                score += 20

        if score > 0:
            scored_chunks.append((score, c))

    scored_chunks.sort(key=lambda x: x[0], reverse=True)
    return [c for _, c in scored_chunks[:top_k]]

class ChatMessage(BaseModel):
    role: str
    content: str

class ChatRequest(BaseModel):
    message: str
    history: Optional[List[ChatMessage]] = []
    model: Optional[str] = "bielik-11b-v3.0-instruct-heretic-mpoa"
    temperature: Optional[float] = 0.0
    top_k: Optional[int] = 5

class FreeMemoryRequest(BaseModel):
    engine: Optional[str] = None
    target_model: Optional[str] = None

@app.on_event("startup")
def startup_event():
    build_or_load_index()

# ----------------- VRAM & RAM MANAGEMENT ENDPOINTS -----------------

def clean_system_vram_and_ram() -> str:
    msg_parts = []
    try:
        res = subprocess.run(["lms", "unload", "--all"], capture_output=True, text=True, timeout=8)
        msg_parts.append("VRAM GPU zwolniony (lms unload)")
    except Exception as e:
        msg_parts.append(f"błąd lms: {e}")

    try:
        gc.collect()
        import ctypes
        ctypes.windll.psapi.EmptyWorkingSet(ctypes.windll.kernel32.GetCurrentProcess())
        msg_parts.append("RAM serwera wyczyszczony")
    except Exception:
        pass
    return " | ".join(msg_parts)

def load_specific_model(model_name: str):
    try:
        subprocess.run(["lms", "load", model_name, "--gpu", "max", "-c", "4096", "-y"], capture_output=True, text=True, timeout=40)
    except Exception as e:
        print(f"[LMS LOAD ERROR] {e}")

@app.post("/api/system/free_memory")
def free_memory_endpoint(req: Optional[FreeMemoryRequest] = None):
    info = clean_system_vram_and_ram()
    if req and req.target_model and req.engine == "LM_STUDIO_RAG":
        load_specific_model(req.target_model)
        info += f" -> Załadowano {req.target_model}"
    return {
        "status": "success",
        "message": f"Pamięć wyczyszczona: {info}",
        "timestamp": time.time()
    }

@app.post("/api/engine/switch")
def switch_engine_endpoint(payload: dict = Body(...)):
    engine = payload.get("target_engine", "LM_STUDIO_RAG")
    model = payload.get("model", "bielik-11b-v3.0-instruct-heretic-mpoa")
    
    clean_system_vram_and_ram()
    if engine == "LM_STUDIO_RAG":
        load_specific_model(model)
        msg = f"Przełączono na LM Studio RAG (Model: {model}). VRAM gotowy."
    else:
        msg = f"Przełączono na {engine}. VRAM karty graficznej zwolniony."
        
    return {"status": "success", "engine": engine, "message": msg}

# ----------------- STATUS & DIAGNOSTICS ENDPOINTS -----------------

@app.get("/api/status")
def get_status():
    lm_status = "offline"
    available_models = []
    try:
        r = requests.get(f"{LM_STUDIO_URL}/models", timeout=2)
        if r.status_code == 200:
            lm_status = "online"
            available_models = [m.get("id") for m in r.json().get("data", [])]
    except Exception:
        pass
    return {
        "status": "online",
        "lm_studio_status": lm_status,
        "available_models": available_models,
        "documents_count": len(documents_db),
        "chunks_count": len(chunks_db),
        "pdf_directory": PDF_DIR
    }

# ----------------- ANYTHINGLLM COMPATIBILITY ENDPOINTS -----------------

@app.get("/api/v1/ping")
def anything_llm_ping():
    return {
        "online": True,
        "status": "ok",
        "engine": "AnythingLLM Compatible RAG Server",
        "documents_indexed": len(documents_db)
    }

@app.post("/api/v1/workspace/{workspace_slug}/chat")
def anything_llm_workspace_chat(workspace_slug: str, payload: dict = Body(...)):
    user_message = payload.get("message") or payload.get("prompt", "")
    
    chat_req = ChatRequest(
        message=user_message,
        model="bielik-11b-v3.0-instruct-heretic-mpoa",
        temperature=0.0,
        top_k=5
    )
    rag_result = chat_rag(chat_req)
    
    formatted_sources = []
    for s in rag_result.get("sources", []):
        formatted_sources.append({
            "title": s["filename"],
            "text": s["snippet"],
            "page": s["page"],
            "url": s["page_image_url"]
        })
        
    return {
        "textResponse": rag_result.get("response", ""),
        "sources": formatted_sources,
        "type": "textResponse",
        "close": True,
        "error": None
    }

# ----------------- PDF VISUAL RENDERING & DOWNLOAD -----------------

@app.get("/api/pdf/page_image")
def get_pdf_page_image(
    filename: str = Query(..., description="Nazwa pliku PDF"),
    page: int = Query(1, description="Numer strony (1-indexed)"),
    dpi: int = Query(140, description="Rozdzielczość renderowania DPI")
):
    safe_name = os.path.basename(filename)
    pdf_path = os.path.join(PDF_DIR, safe_name)
    if not os.path.exists(pdf_path):
        raise HTTPException(status_code=404, detail=f"Plik PDF nie istnieje: {safe_name}")

    try:
        doc = pymupdf.open(pdf_path)
        total_p = len(doc)
        page_idx = max(0, min(page - 1, total_p - 1))
        pdf_page = doc.load_page(page_idx)
        pix = pdf_page.get_pixmap(dpi=dpi)
        img_bytes = pix.tobytes("jpeg")
        doc.close()
        return Response(content=img_bytes, media_type="image/jpeg")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Błąd renderowania strony PDF: {e}")

@app.get("/api/pdf/download")
def download_pdf(filename: str = Query(..., description="Nazwa pliku PDF")):
    safe_name = os.path.basename(filename)
    pdf_path = os.path.join(PDF_DIR, safe_name)
    if not os.path.exists(pdf_path):
        raise HTTPException(status_code=404, detail=f"Plik PDF nie istnieje: {safe_name}")
    return FileResponse(pdf_path, media_type="application/pdf", filename=safe_name)

# ----------------- MAIN RAG CHAT LOGIC (MULTILINGUAL TO POLISH IN-FLIGHT) -----------------

@app.post("/api/chat")
def chat_rag(req: ChatRequest):
    relevant_chunks = search_relevant_chunks(req.message, top_k=req.top_k or 5)
    context_text = ""
    sources = []
    for idx, c in enumerate(relevant_chunks, 1):
        context_text += f"\n--- DOKUMENT [{idx}]: {c['filename']} (Strona {c['page']}) ---\n{c['text']}\n"
        tot_pages = c.get("total_pages") or doc_pages_map.get(c["filename"], 1)
        sources.append({
            "id": idx,
            "filename": c["filename"],
            "brand": c["brand"],
            "page": c["page"],
            "total_pages": tot_pages,
            "page_image_url": f"/api/pdf/page_image?filename={c['filename']}&page={c['page']}",
            "download_url": f"/api/pdf/download?filename={c['filename']}",
            "snippet": c["text"][:240] + "..."
        })

    system_prompt = (
        "Jesteś elitarnym mistrzem mechaniki i diagnostyki maszyn ciężkich, pojazdów i silników (Cure Drucik RAG AI).\n"
        "Baza dokumentacji zawiera fabryczne książki serwisowe w różnych językach (głównie po angielsku, niemiecku, włosku i polsku).\n\n"
        "BEZWZGLĘDNE ZASADY GENEROWANIA ODPOWIEDZI:\n"
        "1. JĘZYK POLSKI: Zawsze odpowiadaj w 100% po polsku. Jeśli dokumentacja jest w języku angielskim, niemieckim lub innym – przetłumacz w locie procedury, nazwy komponentów, instrukcje i uwagi na naturalny język polski.\n"
        "2. PRECYZJA LICZBOWA: Przepisz z absolutną dokładnością wszystkie parametry fizyczne, procedury dokręcania (Nm, ft-lb, kąty w stopniach °), luzy zaworowe (mm, cale), ciśnienia (bar, kPa, psi), kody błędów DTC oraz etapy (np. Krok 1, Krok 2).\n"
        "3. STRUKTURA: Przedstaw odpowiedź w przejrzystych punktach, aby mechanik w warsztacie mógł od razu wykonać procedurę na maszynie.\n"
        "4. PODAWANIE ŹRÓDEŁ: Na końcu odpowiedzi wskaż dokument i stronę, z której pochodzą dane, w formacie np.: [Źródło: Nazwa_Pliku.pdf, str. X].\n"
        "5. BRAK DANYCH: Jeśli w załączonym kontekście brakuje dokładnej procedury lub momentu, napisz wprost: 'W załączonych fragmentach dokumentacji nie odnaleziono bezpośredniej wartości dla tego zapytania'.\n\n"
        f"=== FRAGMENTY FABRYCZNEJ DOKUMENTACJI TECHNICZNEJ ===\n{context_text}"
    )

    messages = [{"role": "system", "content": system_prompt}]
    if req.history:
        for h in req.history[-4:]: messages.append({"role": h.role, "content": h.content})
    messages.append({"role": "user", "content": req.message})

    payload = {
        "model": req.model or "bielik-11b-v3.0-instruct-heretic-mpoa",
        "messages": messages,
        "temperature": req.temperature or 0.0,
        "max_tokens": 1500
    }

    try:
        r = requests.post(f"{LM_STUDIO_URL}/chat/completions", json=payload, timeout=60)
        bot_response = r.json()["choices"][0]["message"]["content"]
    except Exception as e:
        bot_response = f"Błąd połączenia z LM Studio: {e}"

    return {"response": bot_response, "sources": sources, "model_used": req.model}
