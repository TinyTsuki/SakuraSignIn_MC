package xin.vanilla.sakura.client.gui;

import org.junit.Test;
import org.objectweb.asm.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PersonalDateInputWiringTest {
    private static final String SCREEN = "xin/vanilla/sakura/screen/PersonalDateConfigScreen";
    private static final String FORM = "xin/vanilla/sakura/client/gui/PersonalDateInputForm";
    private static final String SESSION = "xin/vanilla/sakura/client/ui/PersonalDateUiSession";
    private static final String SLOT = "xin/vanilla/sakura/data/personaldate/PlayerPersonalDateSlot";

    @Test
    public void screenDateActionDelegatesWithCapturedParentLifetime() throws Exception {
        Calls open = methods(SCREEN).get("openDateInput");
        assertNotNull("Actual personal-date input entry point is missing", open);
        assertTrue("Actual openDateInput must use the validated date form",
                open.invocations.stream().anyMatch(call -> call.owner.equals(FORM) && call.name.equals("create")));
        assertTrue("Child callback must capture the original parent session lifetime",
                open.references.stream().anyMatch(handle -> handle.getOwner().equals(SESSION)
                        && handle.getName().equals("acceptsChildResult")));
    }

    @Test
    public void actualDateCallbackParsesCompletelyBeforeClaimingOrMutatingSlot() throws Exception {
        Map<String, Calls> screen = methods(SCREEN);
        Calls open = screen.get("openDateInput");
        assertNotNull(open);
        boolean delegates = open.invocations.stream()
                .anyMatch(call -> call.owner.equals(FORM) && call.name.equals("create"));
        // Before extraction, inspect the real screen callback so the old source fails on its unsafe order.
        Map<String, Calls> owner = delegates ? methods(FORM) : screen;
        List<Calls> callbacks = owner.values().stream().filter(calls ->
                calls.invocations.stream().anyMatch(call -> call.owner.equals("java/util/concurrent/atomic/AtomicBoolean")
                        && call.name.equals("compareAndSet"))
                        && calls.invocations.stream().anyMatch(PersonalDateInputWiringTest::mutatesSlot)).collect(java.util.stream.Collectors.toList());
        assertEquals("Expected one actual date submission callback", 1, callbacks.size());
        List<Call> calls = callbacks.get(0).invocations;
        int lastParse = -1, claim = -1, firstMutation = -1;
        for (int index = 0; index < calls.size(); index++) {
            Call call = calls.get(index);
            if (call.owner.equals("java/lang/Integer") && call.name.equals("parseInt")) lastParse = index;
            if (call.owner.equals("java/util/concurrent/atomic/AtomicBoolean") && call.name.equals("compareAndSet"))
                claim = index;
            if (firstMutation < 0 && mutatesSlot(call)) firstMutation = index;
        }
        assertTrue("Date submission must parse numeric values before mutation", lastParse >= 0);
        assertTrue("Complete date parsing must precede consuming the one-shot callback", lastParse < claim);
        assertTrue("Slot mutation must follow complete parsing and the one-shot claim", claim < firstMutation);
    }

    @Test
    public void nativeCloseFallbackClosesPersonalSessionBeforeTerminalSuperClose() throws Exception {
        Calls close = methods(SCREEN).get("onClose");
        assertNotNull("Actual native close hook is missing", close);
        int request = -1, sessionClose = -1, terminalClose = -1;
        for (int index = 0; index < close.invocations.size(); index++) {
            Call call = close.invocations.get(index);
            if (call.owner.equals(SESSION) && call.name.equals("requestClose")) request = index;
            if (call.owner.equals(SESSION) && call.name.equals("close")) sessionClose = index;
            if (call.opcode == Opcodes.INVOKESPECIAL && call.name.equals("onClose")
                    && call.owner.equals("xin/vanilla/banira/client/gui/PlayerConfigScreen")) terminalClose = index;
        }
        assertTrue("Actual native fallback must retain the parent close hook", terminalClose >= 0);
        assertTrue("Actual native onClose fallback must close the personal session before terminal super.onClose",
                sessionClose >= 0 && sessionClose < terminalClose);
        assertTrue("Terminal session close must follow the active-view close request", request >= 0 && request < sessionClose);
    }

    @Test
    public void removingTheViewForSuspensionDoesNotCloseThePersonalSession() throws Exception {
        Calls removed = methods(SCREEN).get("onRemoved");
        assertNotNull("Actual native removal hook is missing", removed);
        assertTrue("Removal must still notify the shared UI owner", removed.invocations.stream()
                .anyMatch(call -> call.owner.equals("xin/vanilla/banira/api/client/ui/BaniraUi")
                        && call.name.equals("screenRemoved")));
        assertTrue("Removal must retain native superclass cleanup", removed.invocations.stream()
                .anyMatch(call -> call.opcode == Opcodes.INVOKESPECIAL && call.name.equals("onRemoved")
                        && call.owner.equals("xin/vanilla/banira/client/gui/PlayerConfigScreen")));
        assertFalse("A suspended or detached view must not terminate its active child's parent session",
                removed.invocations.stream().anyMatch(call -> call.owner.equals(SESSION) && call.name.equals("close")));
    }

    private static boolean mutatesSlot(Call call) {
        return call.owner.equals(SLOT) && (call.name.equals("setMonth") || call.name.equals("setDay"));
    }

    private static Map<String, Calls> methods(String owner) throws Exception {
        Map<String, Calls> methods = new LinkedHashMap<>();
        try (InputStream input = PersonalDateInputWiringTest.class.getClassLoader()
                .getResourceAsStream(owner + ".class")) {
            assertNotNull("Actual production class is missing: " + owner, input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    Calls calls = new Calls();
                    methods.put(name, calls);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String target, String method,
                                                    String descriptor, boolean isInterface) {
                            calls.invocations.add(new Call(target, method, opcode));
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor,
                                                           Handle bootstrap, Object... arguments) {
                            for (Object argument : arguments)
                                if (argument instanceof Handle) calls.references.add(((Handle) argument));
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return methods;
    }

    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    @lombok.AllArgsConstructor
    private static class Call {
        String owner;
        String name;
        int opcode;

    }

    private static final class Calls {
        final List<Call> invocations = new ArrayList<>();
        final List<Handle> references = new ArrayList<>();
    }
}
