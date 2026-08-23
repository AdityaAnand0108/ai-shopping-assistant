"""Builds SETUP-GUIDE.pdf: clean clone to running application."""
import io, os

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate, CondPageBreak, Frame, KeepTogether, PageTemplate, Paragraph, Spacer, Table, TableStyle,
)

OUT = 'C:/Application/shop-assistant/docs/SETUP-GUIDE.pdf'

INK = colors.HexColor('#1c1c1f')
MUTED = colors.HexColor('#5f5f68')
ACCENT = colors.HexColor('#1f5eff')
RULE = colors.HexColor('#d8d8de')
CODE_BG = colors.HexColor('#f4f4f6')
WARN_BG = colors.HexColor('#fff6e5')
WARN_BORDER = colors.HexColor('#e8bf76')

styles = getSampleStyleSheet()

H1 = ParagraphStyle('H1', parent=styles['Title'], fontName='Helvetica-Bold',
                    fontSize=22, leading=26, textColor=INK, alignment=TA_LEFT,
                    spaceAfter=2)
SUB = ParagraphStyle('SUB', parent=styles['Normal'], fontSize=10.5, leading=15,
                     textColor=MUTED, spaceAfter=14)
H2 = ParagraphStyle('H2', parent=styles['Heading1'], fontName='Helvetica-Bold',
                    fontSize=14, leading=18, textColor=INK, spaceBefore=16, spaceAfter=6)
H3 = ParagraphStyle('H3', parent=styles['Heading2'], fontName='Helvetica-Bold',
                    fontSize=11, leading=15, textColor=INK, spaceBefore=10, spaceAfter=4)
BODY = ParagraphStyle('BODY', parent=styles['Normal'], fontSize=9.8, leading=14.5,
                      textColor=INK, spaceAfter=6)
SMALL = ParagraphStyle('SMALL', parent=BODY, fontSize=8.8, leading=12.5, textColor=MUTED)
CELL = ParagraphStyle('CELL', parent=BODY, fontSize=8.8, leading=12, spaceAfter=0)
CELLB = ParagraphStyle('CELLB', parent=CELL, fontName='Helvetica-Bold')
CODE = ParagraphStyle('CODE', parent=styles['Code'], fontName='Courier',
                      fontSize=8.8, leading=12.5, textColor=INK, spaceAfter=0,
                      leftIndent=0, rightIndent=0)


def code(*lines):
    """A shaded command block. Lines starting with # render as comments."""
    body = '<br/>'.join(
        f'<font color="#6a6a72">{l}</font>' if l.strip().startswith('#') else l
        for l in lines
    )
    t = Table([[Paragraph(body, CODE)]], colWidths=[165 * mm])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), CODE_BG),
        ('BOX', (0, 0), (-1, -1), 0.5, RULE),
        ('LEFTPADDING', (0, 0), (-1, -1), 8),
        ('RIGHTPADDING', (0, 0), (-1, -1), 8),
        ('TOPPADDING', (0, 0), (-1, -1), 7),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 7),
    ]))
    return [t, Spacer(1, 8)]


def note(text):
    t = Table([[Paragraph(text, CELL)]], colWidths=[165 * mm])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), WARN_BG),
        ('BOX', (0, 0), (-1, -1), 0.6, WARN_BORDER),
        ('LEFTPADDING', (0, 0), (-1, -1), 9),
        ('RIGHTPADDING', (0, 0), (-1, -1), 9),
        ('TOPPADDING', (0, 0), (-1, -1), 7),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 7),
    ]))
    return [t, Spacer(1, 10)]


def table(header, rows, widths):
    data = [[Paragraph(h, CELLB) for h in header]]
    data += [[Paragraph(c, CELL) for c in row] for row in rows]
    t = Table(data, colWidths=widths, repeatRows=1)
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), CODE_BG),
        ('LINEBELOW', (0, 0), (-1, 0), 0.7, RULE),
        ('LINEBELOW', (0, 1), (-1, -2), 0.3, RULE),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('LEFTPADDING', (0, 0), (-1, -1), 7),
        ('RIGHTPADDING', (0, 0), (-1, -1), 7),
        ('TOPPADDING', (0, 0), (-1, -1), 6),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
        ('BOX', (0, 0), (-1, -1), 0.5, RULE),
    ]))
    return [t, Spacer(1, 10)]


def step(number, title):
    """A step heading that will not be left stranded at the foot of a page."""
    return [
        CondPageBreak(38 * mm),
        Paragraph(f'<font color="#1f5eff">STEP {number}</font> &nbsp; {title}', H2),
    ]


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont('Helvetica', 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(22 * mm, 12 * mm, 'AI Shopping Assistant - Setup Guide')
    canvas.drawRightString(188 * mm, 12 * mm, f'Page {doc.page}')
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.5)
    canvas.line(22 * mm, 16 * mm, 188 * mm, 16 * mm)
    canvas.restoreState()


story = []
A = story.append
E = story.extend

# ---------------------------------------------------------------- title ----
A(Paragraph('AI Shopping Assistant', H1))
A(Paragraph(
    'Setup guide &mdash; from an empty machine to a running application. '
    'Every command is written out; nothing is assumed to be installed already.', SUB))

E(table(
    ['What you are installing', 'Detail'],
    [['Backend', 'Spring Boot 3.5.15 monolith on Java 21, serving a REST API on port 8080'],
     ['Frontend', 'React 19 + Vite + TypeScript, dev server on port 5173'],
     ['Database', 'H2, created automatically as a file in the project. No install needed'],
     ['AI model', 'Runs locally through Ollama. No API keys, no cloud, no cost'],
     ['Total time', 'About 15 minutes, most of it downloading the language model']],
    [38 * mm, 127 * mm]))

E(note(
    '<b>Read this first.</b> The application needs <b>two terminals</b> running at the '
    'same time &mdash; one for the backend, one for the frontend. Leave both open while '
    'you use the app. Closing a terminal stops that half of the application.'))

# -------------------------------------------------------- prerequisites ----
A(Paragraph('Before you start: what must be installed', H2))
A(Paragraph(
    'Four things. Install each one, then run its check command and confirm you get a '
    'version number rather than an error.', BODY))

E(table(
    ['Software', 'Version needed', 'Check it with', 'Where to get it'],
    [['Git', 'Any recent', '<font face="Courier">git --version</font>',
      'git-scm.com/downloads'],
     ['Java JDK', '<b>21</b> or newer', '<font face="Courier">java -version</font>',
      'adoptium.net (choose Temurin 21)'],
     ['Node.js', '<b>20.19+</b> or <b>22.12+</b>', '<font face="Courier">node -v</font>',
      'nodejs.org (choose LTS)'],
     ['Ollama', 'Any recent', '<font face="Courier">ollama --version</font>',
      'ollama.com/download']],
    [24 * mm, 30 * mm, 48 * mm, 63 * mm]))

A(Paragraph('You do <b>not</b> need to install these', H3))
E(table(
    ['Not needed', 'Why'],
    [['Maven', 'The project includes the Maven wrapper. The <font face="Courier">mvnw</font> '
               'script downloads the correct Maven version on first use.'],
     ['MySQL or any database',
      'H2 runs inside the application and writes to a file. A MySQL profile exists but is optional.'],
     ['An OpenAI or Anthropic API key',
      'The language model runs on your own machine through Ollama.']],
    [38 * mm, 127 * mm]))

E(note(
    '<b>If a check command says "not recognized" or "command not found"</b> right after '
    'you installed something, close every terminal window and open a new one. A terminal '
    'reads the system PATH when it starts, so one opened before the install cannot see '
    'the new program.'))

# ------------------------------------------------------------- step 1 ------
E(step(1, 'Download the two AI models'))
A(Paragraph(
    'Ollama installs a background service that starts on its own. These two commands '
    'download the models the application uses. This is the slow part &mdash; roughly 5 GB '
    'in total &mdash; so start it first and let it run while you read on.', BODY))

E(code(
    '# The chat model - about 4.7 GB',
    'ollama pull qwen2.5:7b',
    '',
    '# The embedding model, used for search - about 274 MB',
    'ollama pull nomic-embed-text'))

A(Paragraph('Confirm both arrived:', BODY))
E(code(
    'ollama list'))

A(Paragraph(
    'You should see <font face="Courier">qwen2.5:7b</font> and '
    '<font face="Courier">nomic-embed-text</font> listed. If the command hangs or is '
    'refused, start the Ollama service manually with '
    '<font face="Courier">ollama serve</font> and try again.', SMALL))

# ------------------------------------------------------------- step 2 ------
E(step(2, 'Clone the repository'))
A(Paragraph('Pick any folder you like, then:', BODY))
E(code(
    'git clone https://github.com/AdityaAnand0108/ai-shopping-assistant.git',
    'cd ai-shopping-assistant'))
A(Paragraph(
    'Every command from here on is run <b>from inside this folder</b>, unless a step says '
    'otherwise.', SMALL))

# ------------------------------------------------------------- step 3 ------
E(step(3, 'Start the backend (terminal 1)'))
A(Paragraph(
    'Use the command for your operating system. Do not install Maven &mdash; the '
    '<font face="Courier">mvnw</font> script in the project handles it.', BODY))

A(Paragraph('Windows (PowerShell or Command Prompt)', H3))
E(code('.\\mvnw.cmd spring-boot:run'))

A(Paragraph('macOS or Linux', H3))
E(code('./mvnw spring-boot:run'))

A(Paragraph(
    '<b>The first run takes 2&ndash;4 minutes.</b> It downloads Maven and every Java '
    'dependency, then creates the database, loads 60 products, and builds the search '
    'index. Later runs take about 10 seconds.', BODY))

A(Paragraph('Wait until you see these lines. The last one is the signal it is ready:', BODY))
E(code(
    'Migrating schema "public" to version "1 - core schema"',
    '...',
    'Seeded 60 products',
    'Seeded 4 demo accounts: [satvik, sarah, rahul, demo]',
    'Seeded 9 demo orders',
    'Indexed 60 products in 6942ms'))

E(note(
    '<b>Do not skip the wait.</b> The web server starts about a second before the demo '
    'data finishes loading. If you open the app in that gap, the catalog looks empty. '
    'Wait for <font face="Courier">Indexed 60 products</font>.'))

# ------------------------------------------------------------- step 4 ------
E(step(4, 'Start the frontend (terminal 2)'))
A(Paragraph(
    'Open a <b>second</b> terminal. Leave the first one running &mdash; the backend must '
    'stay up. Navigate to the same project folder, then:', BODY))

E(code(
    '# From inside the ai-shopping-assistant folder',
    'cd frontend',
    '',
    '# Downloads the frontend packages. First time only, about 30 seconds',
    'npm install',
    '',
    '# Starts the dev server',
    'npm run dev'))

A(Paragraph('You should see:', BODY))
E(code(
    'VITE v8.2.2  ready in 335 ms',
    '',
    '  ->  Local:   http://localhost:5173/'))

# ------------------------------------------------------------- step 5 ------
E(step(5, 'Open the application'))
A(Paragraph(
    'In a browser, go to:', BODY))
E(code('http://localhost:5173'))

A(Paragraph(
    'The catalog appears immediately and needs no account &mdash; browsing is public, as '
    'in any real shop. To use the assistant or view orders, sign in with one of the demo '
    'accounts below.', BODY))

E(table(
    ['Username', 'Password', 'What this account shows'],
    [['<font face="Courier">satvik</font>', '<font face="Courier">Password123</font>',
      '5 orders covering every status - the fullest account to explore'],
     ['<font face="Courier">sarah</font>', '<font face="Courier">Password123</font>',
      '3 orders, one out for delivery'],
     ['<font face="Courier">rahul</font>', '<font face="Courier">Password123</font>',
      '1 cancelled order'],
     ['<font face="Courier">demo</font>', '<font face="Courier">Demo1234</font>',
      'No orders - shows the empty state']],
    [26 * mm, 30 * mm, 109 * mm]))

A(Paragraph(
    'These are synthetic accounts in a local demo database. The passwords are stored '
    'hashed; they are printed here only so the application can be run.', SMALL))

# ------------------------------------------------------------- step 6 ------
E(step(6, 'Check it works'))
A(Paragraph('Sign in as <font face="Courier">satvik</font>, open <b>Assistant</b>, and ask:', BODY))
E(code('What Nike t-shirts do you have?'))

A(Paragraph(
    'The first reply takes 10&ndash;20 seconds because the model is loading into memory; '
    'later replies take 1&ndash;10 seconds. A correct answer lists real products with real '
    'prices, and shows a line beneath it reading '
    '<font face="Courier">Based on: searched the catalog</font>. That line is the point of '
    'the project: it tells you the answer came from a real database call rather than from '
    'the model guessing.', BODY))

# ---------------------------------------------------------- other things ---
A(Paragraph('Other things you can do', H2))

A(Paragraph('Run the test suite', H3))
A(Paragraph(
    'All 159 tests run without Ollama and without a database server &mdash; they use an '
    'in-memory database and a stub model. This is the quickest way to confirm a clone is '
    'healthy, and it works even if the model download has not finished.', BODY))
E(code(
    '# Windows',
    '.\\mvnw.cmd clean verify',
    '',
    '# macOS / Linux',
    './mvnw clean verify'))

A(Paragraph('Start over with fresh data', H3))
A(Paragraph(
    'The database and search index live in a <font face="Courier">data</font> folder '
    'inside the project. Stop the backend, delete that folder, and start it again &mdash; '
    'everything is recreated from scratch.', BODY))
E(code(
    '# Windows',
    'rmdir /s /q data',
    '',
    '# macOS / Linux',
    'rm -rf data'))

A(Paragraph('Use a real MySQL database instead of H2', H3))
A(Paragraph(
    'Optional. Requires a running MySQL 8 server. The same migrations work unchanged.', BODY))
E(code(
    './mvnw spring-boot:run -Dspring-boot.run.profiles=mysql'))
A(Paragraph(
    'Override <font face="Courier">DB_HOST</font>, <font face="Courier">DB_PORT</font>, '
    '<font face="Courier">DB_NAME</font>, <font face="Courier">DB_USER</font> and '
    '<font face="Courier">DB_PASSWORD</font> as environment variables if your server is '
    'not on localhost with the default credentials.', SMALL))

A(Paragraph('Useful URLs while the application is running', H3))
E(table(
    ['URL', 'What it is'],
    [['<font face="Courier">http://localhost:5173</font>', 'The application'],
     ['<font face="Courier">http://localhost:8080/swagger-ui.html</font>',
      'Interactive API documentation'],
     ['<font face="Courier">http://localhost:8080/actuator/health</font>',
      'Health check, including whether the AI model is reachable'],
     ['<font face="Courier">http://localhost:8080/api/products</font>',
      'The catalog as raw JSON, no sign-in required']],
    [86 * mm, 79 * mm]))

# ------------------------------------------------------- troubleshooting ---
A(Paragraph('Troubleshooting', H2))
A(Paragraph(
    'These are the problems people actually hit, in the order they tend to hit them.', BODY))

E(table(
    ['Symptom', 'Cause and fix'],
    [['<b>"java is not recognized"</b>, or the same for node, git or ollama',
      'The terminal was opened before you installed it. Close all terminal windows and '
      'open a new one. If it still fails, the installer did not add the program to your '
      'PATH &mdash; reinstall and accept the default options.'],

     ['<b>"Port 8080 was already in use"</b>',
      'Another copy of the backend is still running. On Windows, open Task Manager and end '
      'the <font face="Courier">java.exe</font> processes, or run: '
      '<font face="Courier">taskkill /F /IM java.exe</font>. On macOS or Linux: '
      '<font face="Courier">lsof -ti:8080 | xargs kill -9</font>.'],

     ['<b>The catalog page is empty</b>',
      'You opened the app before the backend finished loading its demo data. Wait for '
      '<font face="Courier">Indexed 60 products</font> in terminal 1, then refresh.'],

     ['<b>"The assistant is unavailable"</b> when you send a message',
      'Ollama is not running, or the chat model was not downloaded. Run '
      '<font face="Courier">ollama list</font> and confirm '
      '<font face="Courier">qwen2.5:7b</font> is there. If the command itself fails, run '
      '<font face="Courier">ollama serve</font>.'],

     ['<b>The first reply takes 20 seconds</b>',
      'Expected. The model is being loaded into memory on first use. Subsequent replies '
      'are much faster. If every reply is slow, the machine is short of RAM &mdash; 16 GB '
      'is comfortable, 8 GB will struggle.'],

     ['<b>"Could not reach the server"</b> in the browser',
      'The backend is not running. Check terminal 1 for errors and restart it.'],

     ['<b>npm install fails</b>',
      'Check <font face="Courier">node -v</font> reports 20.19 or higher. Older versions '
      'cannot build this frontend. If it still fails, delete the '
      '<font face="Courier">frontend/node_modules</font> folder and try again.'],

     ['<b>Signing in says "Invalid username or password"</b> for a demo account',
      'Passwords are case sensitive: <font face="Courier">Password123</font> with a '
      'capital P. Note that five wrong attempts lock an account for 15 minutes.'],

     ['<b>The assistant answers, but a reply carries an amber warning</b>',
      'Working as designed. The application checks every answer against what the database '
      'actually returned, and flags any figure it cannot verify. It is telling you the '
      'model said something unsupported.']],
    [52 * mm, 113 * mm]))

# ------------------------------------------------------------ shutdown -----
# Kept as one block so the closing note cannot be stranded alone on a page.
A(KeepTogether([
    Paragraph('Stopping the application', H2),
    Paragraph(
        'Press <b>Ctrl + C</b> in each terminal. On Windows the backend sometimes leaves a '
        'child process behind that keeps port 8080 open; if the next start complains about '
        'the port, end the leftover <font face="Courier">java.exe</font> as described '
        'above.', BODY),
    Spacer(1, 14),
    Paragraph(
        'Nothing in this project reaches the internet after setup. The model runs locally, '
        'the database is a file inside the project folder, and no API keys are required at '
        'any point.', SMALL),
]))


def build():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc = BaseDocTemplate(OUT, pagesize=A4,
                          leftMargin=22 * mm, rightMargin=22 * mm,
                          topMargin=20 * mm, bottomMargin=22 * mm,
                          title='AI Shopping Assistant - Setup Guide',
                          author='ai-shopping-assistant')
    frame = Frame(doc.leftMargin, doc.bottomMargin,
                  doc.width, doc.height, id='body')
    doc.addPageTemplates([PageTemplate(id='main', frames=[frame], onPage=footer)])
    doc.build(story)
    print('wrote', OUT, os.path.getsize(OUT), 'bytes')


if __name__ == '__main__':
    build()
