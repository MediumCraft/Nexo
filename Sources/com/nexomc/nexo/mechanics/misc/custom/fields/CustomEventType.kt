package com.nexomc.nexo.mechanics.misc.custom.fields

import com.nexomc.nexo.mechanics.misc.custom.fields.CustomEventType.CustomListenerConstructor
import com.nexomc.nexo.mechanics.misc.custom.listeners.*
import com.nexomc.nexo.utils.actions.ClickAction

enum class CustomEventType(val constructor: CustomListenerConstructor) {
    BREAK(CustomListenerConstructor { itemID, cooldown, event, clickAction ->
        BreakListener(itemID, cooldown, event, clickAction)
    }),
    CLICK(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        ClickListener(itemID, cooldown, event, clickAction)
    }),
    INV_CLICK(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        InvClickListener(itemID, cooldown, event, clickAction)
    }),
    DROP(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        DropListener(itemID, cooldown, event, clickAction)
    }),
    PICKUP(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        PickupListener(itemID, cooldown, event, clickAction)
    }),
    EQUIP(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        EquipListener(itemID, cooldown, event, clickAction)
    }),
    UNEQUIP(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        UnequipListener(itemID, cooldown, event, clickAction)
    }),
    DEATH(CustomListenerConstructor { itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction ->
        DeathListener(itemID, cooldown, event, clickAction)
    });

    fun interface CustomListenerConstructor {
        fun create(itemID: String?, cooldown: Long, event: CustomEvent, clickAction: ClickAction): CustomListener
    }
}