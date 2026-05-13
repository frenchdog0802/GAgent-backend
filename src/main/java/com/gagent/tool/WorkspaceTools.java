package com.gagent.tool;

import java.util.List;
import java.util.Map;

public class WorkspaceTools {

    public static List<Tool> getTools() {
        return List.of(
            sendEmailTool(),
            addContactTool(),
            getContactByNameTool(),
            listEmailsTool(),
            readEmailTool()
        );
    }

    private static Tool sendEmailTool() {
        return Tool.function(new Function(
            "send_email",
            """
                    Send one email via the user's Gmail. Use only when you already have a valid recipient address.
                    If the user names a person but gives no address, call get_contact_by_name first and use the address from its result—never guess an address.""",
            Parameters.object(
                Map.of(
                    "to_email", new Property("string", "RFC5322 recipient address only (e.g. user@domain.com). Not a display name."),
                    "subject", new Property("string", "Email subject line; use empty string only if the user explicitly wants no subject."),
                    "body", new Property("string", "Plain-text body. Preserve the user's wording for quotes or signatures they asked for.")
                ),
                List.of("to_email", "subject", "body")
            )
        ));
    }

    private static Tool addContactTool() {
        return Tool.function(new Function(
            "add_contact",
            """
                    Create a new Google Contacts entry. Use when the user wants to save someone (name + email at minimum).
                    Do not use for sending mail or for searching existing contacts—use get_contact_by_name or send_email instead.""",
            Parameters.object(
                Map.of(
                    "contact_name", new Property("string", "Full name or how the user referred to them (e.g. 'Jane Doe')."),
                    "email_address", new Property("string", "Primary email for this contact."),
                    "company", new Property("string", "Organization (optional). Omit or empty if unknown."),
                    "notes", new Property("string", "Free-text notes (optional). Omit or empty if unknown.")
                ),
                List.of("contact_name", "email_address")
            )
        ));
    }

    private static Tool getContactByNameTool() {
        return Tool.function(new Function(
            "get_contact_by_name",
            """
                    Search Google Contacts by name or fragment. Use before send_email when the user gives a person name but no email.
                    Prefer a distinctive substring (surname, first name, or 'First Last') over very short queries that match many people.""",
            Parameters.object(
                Map.of(
                    "name_query", new Property("string", "Name or substring to match (e.g. 'Chen', 'Alice', 'Alice Wang').")
                ),
                List.of("name_query")
            )
        ));
    }

    private static Tool listEmailsTool() {
        return Tool.function(new Function(
            "list_emails",
            """
                    List Gmail messages (metadata/summary). First step when the user asks about inbox, unread, recent mail, or finding a message before read_email.
                    Build q using Gmail search operators when helpful: from:, to:, subject:, is:unread, is:read, newer_than:, older_than:, has:attachment, label:. Leave q empty only for generic 'recent inbox' requests.""",
            Parameters.object(
                Map.of(
                    "max_results", new Property("integer", "Max messages to return; if omitted the server defaults to 10. Use 5–20 for 'a few', more for broader searches (Gmail API has its own upper bound)."),
                    "q", new Property("string", "Gmail search string (optional). Examples: 'from:boss is:unread', 'subject:invoice newer_than:7d', 'is:important'.")
                ),
                List.of()
            )
        ));
    }

    private static Tool readEmailTool() {
        return Tool.function(new Function(
            "read_email",
            """
                    Fetch full content for one Gmail message. Always use message_id returned by list_emails (or supplied by the user).
                    Never invent or truncate an id to guess—if missing, call list_emails first.""",
            Parameters.object(
                Map.of(
                    "message_id", new Property("string", "Exact Gmail API message id from a prior list_emails result.")
                ),
                List.of("message_id")
            )
        ));
    }
}
