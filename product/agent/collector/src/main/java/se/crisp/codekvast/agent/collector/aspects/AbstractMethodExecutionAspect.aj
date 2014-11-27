package se.crisp.codekvast.agent.collector.aspects;

import se.crisp.codekvast.agent.collector.InvocationRegistry;

/**
 * This is an AspectJ aspect that captures execution of methods in the scope of interest.
 * <p/>
 * It is weaved into the target app by the AspectJ load-time weaver.
 *
 * @author Olle Hallin
 * @see se.crisp.codekvast.agent.collector.CodekvastCollector
 */
public abstract aspect AbstractMethodExecutionAspect extends AbstractCodekvastAspect {

    /**
     * This abstract pointcut specifies the scope for what method executions to detect.
     * <p/>
     * It is made concrete by an XML file that is created on-the-fly by {@link se.crisp.codekvast.agent.collector.CodekvastCollector} before
     * loading
     * the AspectJ load-time weaving agent.
     */
    public abstract pointcut scope();

    public pointcut methodExecution(): execution(public * *..*(..));

    /**
     * Register that this method has been invoked.
     */
    before(): scope() && methodExecution() && !withinCodekvast() {
        InvocationRegistry.instance.registerMethodInvocation(thisJoinPoint.getSignature());
    }

}
