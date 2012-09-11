;; To install auto-complete, download from git://github.com/m2ym/auto-complete.git
;; Then M-x load-file, auto-complete/etc/install.el

(require 'cl)
(add-to-list 'load-path "~/.emacs.d/")
(require 'auto-complete-config)
(add-to-list 'ac-dictionary-directories "~/.emacs.d//ac-dict")
(ac-config-default)

(require 'clojure-mode)
(add-hook 'clojure-mode-hook (lambda () (paredit-mode +1)))
(add-to-list 'auto-mode-alist '("\\.cljs$" . clojure-mode))
(require 'paredit)
(define-key paredit-mode-map "{" 'paredit-open-brace)
(define-key paredit-mode-map "}" 'paredit-close-brace)
(define-key paredit-mode-map ")" 'paredit-close-parenthesis)
(define-key paredit-mode-map "]" 'paredit-close-bracket)
(define-key paredit-mode-map (kbd "C-M-a") 'backward-up-list)
(define-key paredit-mode-map (kbd "C-M-e") 'up-list)
(provide 'dj)
