import '@testing-library/jest-dom'

// jsdom doesn't implement the modal <dialog> API (used by the lightboxes).
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
}
if (!HTMLDialogElement.prototype.close) {
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute('open')
  }
}
