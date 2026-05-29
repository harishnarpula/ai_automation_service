package com.aiautomationservice.service;

import org.springframework.stereotype.Service;

@Service
public class EmailTemplateService {

    public String wrapInTemplate(String plainTextBody, String clientName) {
        // Strip any agent-generated sign-off since template has its own signature block
        String cleanedBody = stripAgentSignOff(plainTextBody);
        String htmlBody = plainTextToHtml(cleanedBody);

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
          <title>OXYGLOBAL.TECH</title>
          <!--[if mso]>
          <noscript><xml><o:OfficeDocumentSettings>
            <o:PixelsPerInch>96</o:PixelsPerInch>
          </o:OfficeDocumentSettings></xml></noscript>
          <![endif]-->
        </head>
        <body style="margin:0;padding:0;background-color:#f4f5f7;font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;">

          <table width="100%" cellpadding="0" cellspacing="0" border="0"
                 style="background-color:#f4f5f7;padding:32px 16px;">
            <tr>
              <td align="center">

                <table width="620" cellpadding="0" cellspacing="0" border="0"
                       style="max-width:620px;width:100%;border-radius:12px;
                              overflow:hidden;box-shadow:0 2px 16px rgba(0,0,0,0.08);">

                  <!-- HEADER -->
                  <tr>
                    <td style="background:#ffffff;padding:28px 40px 0px 40px;text-align:center;
                               border-bottom:3px solid #1a73e8;">
                      <img src="https://www.oxyglobal.tech/assets/oxyglobal-B5ioIK7_.png"
                           alt="OXYGLOBAL.TECH"
                           width="200"
                           style="display:block;margin:0 auto;border:0;max-width:200px;height:auto;" />
                      <div style="height:20px;"></div>
                    </td>
                  </tr>

                  <!-- BODY -->
                  <tr>
                    <td style="background:#ffffff;padding:36px 40px 28px 40px;">
                      <div style="font-size:15px;line-height:1.8;color:#1f1f1f;">
                        """ + htmlBody + """
                      </div>
                    </td>
                  </tr>

                  <!-- DIVIDER -->
                  <tr>
                    <td style="background:#ffffff;padding:0 40px;">
                      <div style="height:1px;background:#e8e8e8;"></div>
                    </td>
                  </tr>

                  <!-- SIGNATURE -->
                  <tr>
                    <td style="background:#ffffff;padding:22px 40px 36px 40px;
                               border-radius:0 0 12px 12px;">
                      <table cellpadding="0" cellspacing="0" border="0">
                        <tr>
                          <td style="padding-right:14px;vertical-align:middle;">
                            <div style="width:42px;height:42px;border-radius:50%;
                                        background:#1a73e8;line-height:42px;
                                        text-align:center;font-size:17px;">✉️</div>
                          </td>
                          <td style="vertical-align:middle;">
                            <div style="font-size:13px;font-weight:700;color:#1f1f1f;">
                              OXYGLOBAL.TECH</div>
                            <div style="font-size:12px;margin-top:3px;">
                              <a href="mailto:sales@oxyglobaltech.xyz"
                                 style="color:#1a73e8;text-decoration:none;">
                                sales@oxyglobaltech.xyz</a>
                            </div>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>

        </body>
        </html>
        """;
    }

    /**
     * Strips the agent-generated sign-off block from the bottom of the body.
     * The template's own signature block replaces it.
     * Removes lines like: "Warm regards,", "OXYGLOBAL.TECH", "OXYGLOBAL.TECH Team",
     * "sales@oxyglobaltech.xyz", "Best,", "Best regards,"
     */
    private String stripAgentSignOff(String body) {
        if (body == null) return "";

        String[] lines = body.split("\n");
        int cutAt = lines.length;

        // Walk from bottom up, cut at the first sign-off keyword we find.
        // No early break — we must scan past stray lines like "Alex" that
        // appear BELOW the closing keyword ("Warm regards," etc.) and are
        // not themselves keywords.
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim().toLowerCase();
            if (t.isEmpty()) continue;

            if (isSignOffLine(t)) {
                // This line is part of the sign-off block — mark it for removal
                cutAt = i;
            } else if (i >= cutAt) {
                // We are still inside the sign-off region (below a keyword we
                // already flagged), so keep scanning upward even if this line
                // doesn't match a keyword (e.g. a stray "Alex" above the email).
                cutAt = i;
            } else {
                // We have reached real email content — stop
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cutAt; i++) {
            sb.append(lines[i]);
            if (i < cutAt - 1) sb.append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Returns true if this trimmed, lowercased line is a known sign-off phrase.
     */
    private boolean isSignOffLine(String t) {
        return t.startsWith("warm regards")
                || t.startsWith("best regards")
                || t.startsWith("best,")
                || t.startsWith("regards,")
                || t.startsWith("sincerely")
                || t.startsWith("cheers")
                || t.startsWith("thanks,")
                || t.startsWith("thank you,")
                || t.equals("oxyglobal.tech")
                || t.equals("oxyglobal.tech team")
                || t.contains("sales@oxyglobal")
                || t.contains("@oxyglobal")
                // Catch stray agent names on their own line
                || t.equals("alex")
                || t.equals("- alex")
                || t.matches("^[a-z]{2,12}$"); // single short word alone = likely a name sign-off
    }

    /**
     * Converts plain text to HTML with proper paragraph spacing.
     * Double newlines → <p> tags, single newlines → <br/>
     */
    private String plainTextToHtml(String text) {
        if (text == null) return "";

        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        String[] paragraphs = escaped.split("\\n\\s*\\n");
        StringBuilder html = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                String withBreaks = trimmed.replace("\n", "<br/>");
                html.append("<p style=\"margin:0 0 18px 0;\">")
                        .append(withBreaks)
                        .append("</p>");
            }
        }
        return html.toString();
    }
}