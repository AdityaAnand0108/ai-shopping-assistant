/**
 * Components that run on a schedule or once at startup.
 *
 * <p>Both runners here are ordered: demo data is installed first, and the
 * semantic index is built afterwards, because indexing an empty catalog would
 * produce an empty index that then never rebuilds.
 */
package com.shopassist.scheduler;
