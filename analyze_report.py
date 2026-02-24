import sys
import re
import os

REPORT_DIR = "wiremock-core/build/reports/jacoco/test/html"
PACKAGES = ["com.github.tomakehurst.wiremock.stubbing", "com.github.tomakehurst.wiremock"]

TARGET_CLASSES = ["SessionId", "SessionAwareScenarios", "AbstractStubMappings"]

def parse_class_row(html_content, class_name):
    # Regex to find the row for the class
    pattern = re.compile(f'<a href="{class_name}.html"[^>]*>{class_name}</a>.*?</tr>', re.DOTALL)
    match = pattern.search(html_content)
    if not match:
        return None
    
    row = match.group(0)
    
    # Extract ctr2 (Cov %, Totals)
    ctrs2 = re.findall(r'<td class="ctr2"[^>]*>(.*?)</td>', row)
    # ctrs2[0] = Instr Cov %
    # ctrs2[1] = Branch Cov % (or n/a)
    # ctrs2[2] = Total Cxty
    # ctrs2[3] = Total Lines
    
    branch_cov = "N/A"
    if len(ctrs2) > 1:
        branch_cov = ctrs2[1]
        
    # Extract ctr1 (Missed counts)
    ctrs1 = re.findall(r'<td class="ctr1"[^>]*>(.*?)</td>', row)
    # ctrs1[0] = Missed Cxty
    # ctrs1[1] = Missed Lines
    
    missed_lines = 0
    total_lines = 0
    
    if len(ctrs1) > 1:
        missed_lines = int(ctrs1[1].replace(',', ''))
        
    if len(ctrs2) > 3:
        total_lines = int(ctrs2[3].replace(',', ''))
        
    line_cov_pct = 0
    if total_lines > 0:
        line_cov_pct = (total_lines - missed_lines) / total_lines * 100
        
    return {
        "line_cov": f"{line_cov_pct:.1f}%",
        "branch_cov": branch_cov,
        "missed_lines": missed_lines,
        "total_lines": total_lines
    }

def analyze():
    print("Coverage Analysis:")
    print(f"{'Class':<25} {'Line Cov':<10} {'Branch Cov':<10} {'Missed Lines':<15}")
    print("-" * 65)
    
    for cls in TARGET_CLASSES:
        found = False
        for pkg in PACKAGES:
            path = os.path.join(REPORT_DIR, pkg, "index.html")
            if os.path.exists(path):
                with open(path, 'r') as f:
                    content = f.read()
                    data = parse_class_row(content, cls)
                    if data:
                        print(f"{cls:<25} {data['line_cov']:<10} {data['branch_cov']:<10} {data['missed_lines']:<15}")
                        found = True
                        break
        if not found:
            print(f"{cls:<25} {'N/A':<10} {'N/A':<10} {'N/A':<15}")

if __name__ == "__main__":
    analyze()
