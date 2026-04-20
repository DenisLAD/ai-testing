# PowerShell script to update all Action files

$actions = @(
    @{Name="TypeAction"; Type="TYPE"},
    @{Name="ScrollUpAction"; Type="SCROLL_UP"},
    @{Name="ScrollDownAction"; Type="SCROLL_DOWN"},
    @{Name="AssertPresenceAction"; Type="ASSERT_PRESENCE"},
    @{Name="AssertTextAction"; Type="ASSERT_TEXT"},
    @{Name="NavigateBackAction"; Type="NAVIGATE_BACK"},
    @{Name="NavigateForwardAction"; Type="NAVIGATE_FORWARD"},
    @{Name="NavigateToAction"; Type="NAVIGATE_TO"},
    @{Name="RefreshAction"; Type="REFRESH"},
    @{Name="ExploreMenuAction"; Type="EXPLORE_MENU"},
    @{Name="ExploreFormsAction"; Type="EXPLORE_FORMS"},
    @{Name="TestValidationAction"; Type="TEST_VALIDATION"},
    @{Name="ReportIssueAction"; Type="REPORT_ISSUE"},
    @{Name="CompleteAction"; Type="COMPLETE"}
)

$basePath = "E:\Projects2026\ai-testing\ai-testing-core\src\main\java\ru\sbrf\uddk\ai\testing\service\actions"

foreach ($action in $actions) {
    $file = Join-Path $basePath "$($action.Name).java"
    
    if (Test-Path $file) {
        $content = Get-Content $file -Raw
        
        # Add @Component if not exists
        if ($content -notmatch '@Component') {
            $content = $content -replace '(@Slf4j\r?\n)(public class)', '$1@Component`n$2'
            
            # Add import for Component if not exists
            if ($content -notmatch 'import org.springframework.stereotype.Component') {
                $content = $content -replace '(import lombok.extern.slf4j.Slf4j;)', '$1`nimport org.springframework.stereotype.Component;'
            }
            
            # Add getType() method before execute()
            $getTypeMethod = @"
    
    @Override
    public String getType() {
        return "$($action.Type)";
    }
"@
            $content = $content -replace '(\r?\n    @Override\r?\n    public AgentAction execute)', "$getTypeMethod`n$1"
            
            Set-Content $file $content -NoNewline
            Write-Host "Updated: $($action.Name)"
        } else {
            Write-Host "Skip (already has @Component): $($action.Name)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "Not found: $($action.Name)" -ForegroundColor Red
    }
}

Write-Host "`nDone!" -ForegroundColor Green
