package se.crisp.codekvast.agent.sensor.aspects;

/**
 * Abstract base aspect providing some reusable pointcuts.
 *
 * @author Olle Hallin
 */
abstract aspect AbstractCodeKvastAspect {

    pointcut withinCodeKvast(): within(se.crisp.codekvast.agent..*);

}
