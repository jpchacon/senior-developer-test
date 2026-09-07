/**
 * Turns a structural diff into a human-readable account of what changed.
 *
 * <p>Global plane: everything here is firm-independent, so one computation serves every customer
 * on the same version pair.
 *
 * <p>The split of responsibilities is deliberate and load-bearing. A deterministic classifier
 * decides <em>what</em> changed; a language model only decides <em>how to say it</em>; a
 * validator decides <em>whether that phrasing can be trusted</em>; and a deterministic renderer
 * stands ready when it cannot. The model is never the source of truth about the content of an
 * update.
 *
 * <p>See {@code DESIGN.md}, the AI Usage section.
 */
package com.caseware.templateupdate.summary;
