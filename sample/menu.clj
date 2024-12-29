(ns ollama.menu
  (:require [cljfx.api :as fx]))

(defn handle-event [event]
  (case (:event/type event)
    :menu-item/open (println "Open clicked!")
    :menu-item/exit (do (println "Exiting...") (System/exit 0))))

(def menu-bar
  {:fx/type :menu-bar
   :menus [{:fx/type :menu
            :text "File"
            :items [{:fx/type :menu-item
                     :text "Open"
                     :on-action {:event/type :menu-item/open}}
                    {:fx/type :menu-item
                     :text "Exit"
                     :on-action {:event/type :menu-item/exit}}]}
           {:fx/type :menu
            :text "Edit"
            :items [{:fx/type :menu-item
                     :text "Undo"
                     :on-action {:event/type :menu-item/undo}}
                    {:fx/type :menu-item
                     :text "Redo"
                     :on-action {:event/type :menu-item/redo}}]}]})

(defn root-view [_]
  {:fx/type :stage
   :showing true
   :title "Cljfx Menu Example"
   :width 400
   :height 300
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :children [menu-bar]}}})

(defonce *state (atom {}))

(defn -main []
  (fx/mount-renderer *state
                     (fx/create-renderer
                       :middleware (fx/wrap-map-desc assoc :fx/type root-view)
                       :opts {:fx.opt/map-event-handler handle-event})))
