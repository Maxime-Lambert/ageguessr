package app.ageguessr.shared.email;

public record EmailMessage(String to, String subject, String htmlBody) {}
