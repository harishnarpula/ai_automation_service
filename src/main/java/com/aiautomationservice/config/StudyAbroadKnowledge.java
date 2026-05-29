package com.aiautomationservice.config;

/**
 * AskOxy Study Abroad — Knowledge Base
 *
 * This is the single place to update all study abroad facts.
 * WebhookService reads KNOWLEDGE_BASE and injects it into the AI system prompt
 * so the AI gives accurate, specific answers instead of generic ones.
 *
 * HOW TO UPDATE:
 * - Add new countries, universities, programs, fees, deadlines here
 * - Restart the app — changes reflect immediately in all WhatsApp AI replies
 */
public final class StudyAbroadKnowledge {

    private StudyAbroadKnowledge() {}

    public static final String KNOWLEDGE_BASE = """
        ══════════════════════════════════════════════════
        ASKOXY STUDY ABROAD — INTERNAL KNOWLEDGE BASE
        ══════════════════════════════════════════════════

        ABOUT ASKOXY STUDY ABROAD:
        - Part of OxyGroup founded by Radha Krishna Thatavrthi
        - Free counselling for students aspiring to study abroad
        - End-to-end support: university selection → visa → travel
        - Contact: WhatsApp this number | Email: studyabroad@askoxy.ai

        ──────────────────────────────────────────────────
        TOP COUNTRIES WE COUNSEL FOR
        ──────────────────────────────────────────────────

        🇩🇪 GERMANY
        - No tuition fees at public universities (only semester fee ~€350/semester)
        - Popular universities: TU Munich, LMU Munich, RWTH Aachen, TU Berlin, Heidelberg
        - Popular courses: Engineering, Computer Science, MBA, Data Science
        - Intake: Winter (Oct) — apply by May/June | Summer (Apr) — apply by Nov/Dec
        - IELTS: 6.5+ or German B2/C1 for German-taught programs
        - Student visa: German student visa (national visa) required
        - Part-time work: 120 full days or 240 half days per year allowed
        - Post-study work visa: 18 months job-seeking visa after graduation
        - Average living cost: €800–€1,000/month

        🇨🇦 CANADA
        - Popular universities: University of Toronto, UBC, McGill, McMaster, Waterloo
        - Popular courses: MBA, Data Science, Engineering, Nursing, Computer Science
        - Intake: September (main) | January | May
        - IELTS: 6.5–7.0 (varies by university)
        - Student visa: Canadian Study Permit
        - Part-time work: 20 hours/week during studies, full-time during breaks
        - Post-study work: PGWP (up to 3 years based on program length)
        - Average living cost: CAD 1,200–1,800/month
        - Express Entry pathway available after work experience

        🇬🇧 UK
        - Popular universities: Oxford, Cambridge, Imperial, UCL, Edinburgh, Manchester
        - Popular courses: MBA, Law, Finance, Engineering, Medicine
        - Intake: September (main) | January (some programs)
        - IELTS: 6.5–7.5
        - Student visa: UK Student Visa (Tier 4)
        - Part-time work: 20 hours/week during term
        - Post-study work: Graduate Route visa — 2 years (3 years for PhD)
        - Average living cost: £1,200–1,500/month (London higher)

        🇦🇺 AUSTRALIA
        - Popular universities: ANU, University of Melbourne, UNSW, University of Sydney, Monash
        - Popular courses: Engineering, IT, Business, Healthcare, Education
        - Intake: February (main) | July
        - IELTS: 6.5–7.0
        - Student visa: Subclass 500
        - Part-time work: 48 hours per fortnight during studies
        - Post-study work: Temporary Graduate Visa (subclass 485) — 2 to 4 years
        - Average living cost: AUD 1,800–2,500/month

        🇺🇸 USA
        - Popular universities: MIT, Stanford, Harvard, Carnegie Mellon, Purdue, ASU
        - Popular courses: MS Computer Science, MBA, Data Science, Engineering, Finance
        - Intake: Fall (Aug/Sep) — main | Spring (Jan)
        - GRE/GMAT required for most MS/MBA programs
        - IELTS: 6.5–7.5 | TOEFL: 90–110
        - Student visa: F-1 visa
        - Part-time work: 20 hours/week on campus only (CPT/OPT for off-campus)
        - Post-study work: OPT 1 year (3 years for STEM)
        - Average living cost: USD 1,500–2,500/month

        🇮🇪 IRELAND
        - Popular universities: Trinity College Dublin, UCD, DCU, NUI Galway
        - Popular courses: Data Analytics, Computer Science, Business, Pharma
        - Intake: September | January
        - IELTS: 6.5+
        - Part-time work: 20 hours/week during term, 40 hours during holidays
        - Post-study work: Stay Back visa — 1 to 2 years
        - Average living cost: €1,000–1,400/month

        ──────────────────────────────────────────────────
        SCHOLARSHIPS WE HELP WITH
        ──────────────────────────────────────────────────
        - DAAD Scholarship (Germany) — fully funded, for Masters/PhD
        - Chevening Scholarship (UK) — fully funded, for Masters
        - Australia Awards — fully funded
        - Vanier Canada Graduate Scholarship
        - Erasmus+ (European universities)
        - University merit scholarships (vary by institution)
        - Education loans available via OxyLoans for eligible students

        ──────────────────────────────────────────────────
        LANGUAGE TEST REQUIREMENTS
        ──────────────────────────────────────────────────
        - IELTS: Accepted by UK, Canada, Australia, Germany, Ireland
        - TOEFL: Accepted by USA, Canada (some universities)
        - PTE: Accepted by Australia, UK, Canada
        - German A2/B1/B2/C1: Required for German-taught programs
        - Duolingo: Accepted by some Canadian and US universities

        ──────────────────────────────────────────────────
        TYPICAL PROCESS (END TO END)
        ──────────────────────────────────────────────────
        1. Free counselling session with AskOxy advisor
        2. Profile evaluation — academic background, work experience, test scores
        3. Country and university shortlisting
        4. SOP (Statement of Purpose) + LOR guidance
        5. Application submission
        6. Offer letter received
        7. Scholarship application (if applicable)
        8. Visa application and documentation support
        9. Pre-departure briefing
        10. Post-arrival support

        ──────────────────────────────────────────────────
        IMPORTANT DEADLINES (General Guidance)
        ──────────────────────────────────────────────────
        - Germany Winter intake: Apply May–July
        - UK September intake: Apply October–January (UCAS deadline Jan 31)
        - Canada September intake: Apply November–February
        - Australia February intake: Apply September–November
        - USA Fall intake: Apply October–February
        - Ireland September intake: Apply February–May

        ──────────────────────────────────────────────────
        EDUCATION LOAN SUPPORT (via OxyLoans)
        ──────────────────────────────────────────────────
        - Education loans available for Indian students going abroad
        - Collateral and non-collateral options
        - Partnered with leading NBFCs and banks
        - Contact our team for loan eligibility check

        ══════════════════════════════════════════════════
        NOTE TO AI: Use this knowledge to answer student
        queries accurately. If a specific detail is not
        listed here, say "Please connect with our counselor
        for exact details" — do NOT guess or make up facts.
        ══════════════════════════════════════════════════
        """;
}