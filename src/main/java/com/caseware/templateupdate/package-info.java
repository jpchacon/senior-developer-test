/**
 * Tells firms which engagement files have pending product-template updates, and what changed.
 *
 * <p>The system is split into two planes. The <em>global</em> plane ({@code summary},
 * {@code cache}) works on template content only and is shared across every customer. The
 * <em>tenant</em> plane ({@code resolver}, and the per-firm side of {@code persistence}) works on
 * one firm's engagement state. Keeping the two apart is what makes a single summary computation
 * serve every firm safely.
 *
 * <p>See {@code DESIGN.md} and {@code DIAGRAMS.md}.
 */
package com.caseware.templateupdate;
