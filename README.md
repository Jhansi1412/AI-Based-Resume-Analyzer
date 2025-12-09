# AI Resume Analyzer (Java + Spring Boot + OpenAI/Offline Heuristic)

An AI-powered web app that analyzes how well a **resume** matches a **job description**.

Users can upload resume and JD files in **PDF / DOC / DOCX / TXT** format.  
The app extracts the text, compares them using **OpenAI** (with a local Java fallback), and shows:

- Overall **match score** (with circular gauge + bar)
- **Matched skills** vs **missing skills** (colored chips)
- Concrete **suggestions** to improve the resume
- Extracted text previews of resume and JD

> ✅ Tech-stack ready for a resume / portfolio project  
> ✅ Handles real documents (PDF/DOC/DOCX/TXT)  
> ✅ Works even if OpenAI quota is exceeded (offline heuristic mode)

---

## 🔧 Tech Stack

**Backend**

- Java 17+ (tested with Java 23)
- Spring Boot 3.3.x
- Maven

**AI & Analysis**

- OpenAI Chat Completions API (`gpt-4o-mini` by default)
- Custom **offline heuristic analyzer** (keyword-based) as fallback when API quota is exceeded

**File Parsing**

- Apache Tika 2.9.2  
  → Auto-detects and extracts text from: PDF, DOC, DOCX, TXT

**Frontend**

- Spring MVC + Thymeleaf
- HTML5 + CSS3 (custom dark theme UI)
- CSS-based circular gauge & progress bar for visualization

---

## ✨ Features

### 1. File Upload (PDF / DOC / DOCX / TXT)

- Upload **Resume** (PDF, DOC, DOCX, or TXT)
- Upload **Job Description** (PDF, DOC, DOCX, or TXT)
- Files are parsed using **Apache Tika** to extract plain text

### 2. AI Analysis (OpenAI + Fallback)

- The app sends extracted texts (resume + JD) to the **OpenAI Chat Completions API**
- The model is instructed to return a clean JSON structure:

  ```json
  {
    "matchScore": 0-100,
    "matchedSkills": ["Java", "Spring Boot", "..."],
    "missingSkills": ["Docker", "Kubernetes", "..."],
    "suggestions": ["sentence1", "sentence2", "..."]
  }
